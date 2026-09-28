package com.tutorcraft.core.assessment.quiz.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.assessment.quiz.application.QuestionRepository.QuestionFilter;
import com.tutorcraft.core.assessment.quiz.application.QuizViews.CategoryView;
import com.tutorcraft.core.assessment.quiz.application.QuizViews.PreviewCheckView;
import com.tutorcraft.core.assessment.quiz.application.QuizViews.QuestionSummaryView;
import com.tutorcraft.core.assessment.quiz.application.QuizViews.QuestionVersionView;
import com.tutorcraft.core.assessment.quiz.application.QuizViews.QuestionView;
import com.tutorcraft.core.assessment.quiz.domain.AttemptScoring;
import com.tutorcraft.core.assessment.quiz.domain.GradeOutcome;
import com.tutorcraft.core.assessment.quiz.domain.QuestionCategory;
import com.tutorcraft.core.assessment.quiz.domain.QuestionDraft;
import com.tutorcraft.core.assessment.quiz.domain.QuestionGrader;
import com.tutorcraft.core.assessment.quiz.domain.QuestionResponse;
import com.tutorcraft.core.assessment.quiz.domain.QuestionVersion;
import com.tutorcraft.core.assessment.quiz.domain.StoredQuestion;
import com.tutorcraft.core.audit.AuditLog;
import com.tutorcraft.core.audit.AuditRecord;
import com.tutorcraft.core.courses.CoursesApi;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.api.PageResponse;
import com.tutorcraft.core.shared.domain.Ids;
import com.tutorcraft.core.shared.domain.NotFoundException;
import com.tutorcraft.core.shared.domain.Validator;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Банк вопросов курса: папки, вопросы, версии (FR-QBANK-01/02/05). Все операции требуют {@code qbank.manage}. */
@Service
public class QuestionBankService {

    private static final Logger log = LoggerFactory.getLogger(QuestionBankService.class);
    private static final int MAX_CATEGORY_NAME = 255;
    private static final String OBJECT_TYPE = "question";

    private final QuestionRepository questions;
    private final QuestionCategoryRepository categories;
    private final QuizLayoutRepository layouts;
    private final QuestionVersionFactory versionFactory;
    private final CoursesApi courses;
    private final AccessService access;
    private final CurrentUserProvider currentUser;
    private final AuditLog audit;
    private final Clock clock;

    public QuestionBankService(QuestionRepository questions, QuestionCategoryRepository categories, QuizLayoutRepository layouts,
                               QuestionVersionFactory versionFactory, CoursesApi courses, AccessService access,
                               CurrentUserProvider currentUser, AuditLog audit, Clock clock) {
        this.questions = questions;
        this.categories = categories;
        this.layouts = layouts;
        this.versionFactory = versionFactory;
        this.courses = courses;
        this.access = access;
        this.currentUser = currentUser;
        this.audit = audit;
        this.clock = clock;
    }

    public List<CategoryView> categories(UUID courseId) {
        CurrentUser user = requireBank(courseId);
        Map<UUID, Long> counts = questions.countByCategory(user.tenantId(), courseId);
        return categories.listOfCourse(user.tenantId(), courseId).stream()
                .map(c -> new CategoryView(c.id(), c.parentId(), c.name(), counts.getOrDefault(c.id(), 0L))).toList();
    }

    public CategoryView createCategory(UUID courseId, String name, UUID parentId) {
        CurrentUser user = requireBank(courseId);
        new Validator().notBlank(name, "name").maxLength(name, MAX_CATEGORY_NAME, "name").throwIfInvalid();
        if (parentId != null) {
            requireCategory(user.tenantId(), courseId, parentId, "parentId");
        }
        QuestionCategory category = new QuestionCategory(Ids.newId(), user.tenantId(), courseId, parentId, name.strip(),
                clock.instant());
        categories.insert(category);
        return new CategoryView(category.id(), parentId, category.name(), 0);
    }

    public PageResponse<QuestionSummaryView> list(UUID courseId, QuestionFilter filter, PageQuery page) {
        CurrentUser user = requireBank(courseId);
        PageResponse<StoredQuestion> found = questions.search(user.tenantId(), courseId, filter, page);
        Map<UUID, Integer> usage = layouts.usage(user.tenantId(), found.items().stream().map(StoredQuestion::id).toList());
        return found.map(q -> summary(q, usage.getOrDefault(q.id(), 0)));
    }

    @Transactional
    public QuestionView create(UUID courseId, QuestionDraft draft) {
        CurrentUser user = requireBank(courseId);
        requireOptionalCategory(user.tenantId(), courseId, draft.categoryId());
        UUID questionId = Ids.newId();
        QuestionVersionFactory.NewVersion created = versionFactory.create(user, courseId, questionId, null, draft);
        questions.save(created.question(), created.version());
        audit.record(AuditRecord.of(user.tenantId(), user.userId(), "question.created", OBJECT_TYPE, questionId.toString())
                .withContext("course:" + courseId));
        log.info("Question {} created in course {}", questionId, courseId);
        return QuestionMapper.view(created.question(), created.version());
    }

    public QuestionView get(UUID questionId) {
        StoredQuestion question = requireManaged(questionId);
        return QuestionMapper.view(question, questions.findVersion(question.tenantId(), question.currentVersionId())
                .orElseThrow(QuestionBankService::notFound));
    }

    /** Правка создаёт новую версию; попытки продолжают ссылаться на старую (FR-QBANK-05, DATA-02). */
    @Transactional
    public QuestionView update(UUID questionId, QuestionDraft draft) {
        StoredQuestion current = requireManaged(questionId);
        CurrentUser user = currentUser.require();
        requireOptionalCategory(user.tenantId(), current.courseId(), draft.categoryId());
        QuestionVersionFactory.NewVersion created = versionFactory.create(user, current.courseId(), questionId, current, draft);
        questions.save(created.question(), created.version());
        audit.record(AuditRecord.of(user.tenantId(), user.userId(), "question.updated", OBJECT_TYPE, questionId.toString())
                .withDiff(Map.of("version", created.version().version())));
        log.info("Question {} updated to version {}", questionId, created.version().version());
        return QuestionMapper.view(created.question(), created.version());
    }

    @Transactional
    public void delete(UUID questionId) {
        StoredQuestion question = requireManaged(questionId);
        CurrentUser user = currentUser.require();
        Instant now = clock.instant();
        questions.markDeleted(user.tenantId(), questionId, now);
        audit.record(AuditRecord.of(user.tenantId(), user.userId(), "question.deleted", OBJECT_TYPE, questionId.toString())
                .withContext("course:" + question.courseId()));
    }

    public List<QuestionVersionView> versions(UUID questionId) {
        StoredQuestion question = requireManaged(questionId);
        return questions.versions(question.tenantId(), questionId).stream()
                .map(v -> new QuestionVersionView(v.version(), v.createdAt(), v.id())).toList();
    }

    /** Предпросмотр «как студент» с проверкой ответа (FR-QBANK-06). Эссе оценивается вручную — 0 баллов. */
    public PreviewCheckView previewCheck(UUID questionId, Map<String, Object> rawResponse) {
        StoredQuestion question = requireManaged(questionId);
        QuestionVersion version = questions.findVersion(question.tenantId(), question.currentVersionId())
                .orElseThrow(QuestionBankService::notFound);
        GradeOutcome outcome = QuestionGrader.grade(version.data(), QuestionResponse.parse(rawResponse));
        BigDecimal score = outcome.manual() ? BigDecimal.ZERO : AttemptScoring.slotScore(version.defaultScore(), outcome.fraction());
        return new PreviewCheckView(score, version.defaultScore(), outcome.isFullyCorrect());
    }

    /** Вопрос банка, доступный текущему пользователю с правом qbank.manage в курсе вопроса. */
    StoredQuestion requireManaged(UUID questionId) {
        CurrentUser user = currentUser.require();
        StoredQuestion question = questions.find(user.tenantId(), questionId).orElseThrow(QuestionBankService::notFound);
        access.require(Permission.QBANK_MANAGE, AccessContext.course(question.courseId()));
        return question;
    }

    private CurrentUser requireBank(UUID courseId) {
        CurrentUser user = currentUser.require();
        courses.requireCourse(user.tenantId(), courseId);
        access.require(Permission.QBANK_MANAGE, AccessContext.course(courseId));
        return user;
    }

    private void requireOptionalCategory(UUID tenantId, UUID courseId, UUID categoryId) {
        if (categoryId != null) {
            requireCategory(tenantId, courseId, categoryId, "categoryId");
        }
    }

    private void requireCategory(UUID tenantId, UUID courseId, UUID categoryId, String field) {
        categories.find(tenantId, categoryId).filter(category -> category.courseId().equals(courseId))
                .orElseThrow(() -> new NotFoundException(QuizErrors.CATEGORY_NOT_FOUND, "Question category not found",
                        Map.of("field", field)));
    }

    private static QuestionSummaryView summary(StoredQuestion question, int usage) {
        return new QuestionSummaryView(question.id(), question.type().key(), question.title(), question.tags(),
                question.currentVersion(), question.updatedAt(), usage);
    }

    static NotFoundException notFound() {
        return new NotFoundException(QuizErrors.QUESTION_NOT_FOUND, "Question not found");
    }
}
