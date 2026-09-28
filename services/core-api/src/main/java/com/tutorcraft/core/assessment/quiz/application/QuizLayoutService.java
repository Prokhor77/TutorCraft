package com.tutorcraft.core.assessment.quiz.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.assessment.quiz.application.QuizItems.QuizItem;
import com.tutorcraft.core.assessment.quiz.application.QuizViews.LayoutView;
import com.tutorcraft.core.assessment.quiz.application.QuizViews.QuestionSummaryView;
import com.tutorcraft.core.assessment.quiz.application.QuizViews.RandomView;
import com.tutorcraft.core.assessment.quiz.application.QuizViews.SlotView;
import com.tutorcraft.core.assessment.quiz.domain.LayoutSlot;
import com.tutorcraft.core.assessment.quiz.domain.QuizLayout;
import com.tutorcraft.core.assessment.quiz.domain.StoredQuestion;
import com.tutorcraft.core.audit.AuditLog;
import com.tutorcraft.core.audit.AuditRecord;
import com.tutorcraft.core.shared.domain.FieldViolation;
import com.tutorcraft.core.shared.domain.ValidationException;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Состав теста: фиксированные и случайные слоты (FR-QUIZ-02). Требует {@code quiz.manage}. */
@Service
public class QuizLayoutService {

    private final QuizItems quizzes;
    private final QuizLayoutRepository layouts;
    private final QuestionRepository questions;
    private final QuestionCategoryRepository categories;
    private final AccessService access;
    private final CurrentUserProvider currentUser;
    private final AuditLog audit;

    public QuizLayoutService(QuizItems quizzes, QuizLayoutRepository layouts, QuestionRepository questions,
                             QuestionCategoryRepository categories, AccessService access, CurrentUserProvider currentUser,
                             AuditLog audit) {
        this.quizzes = quizzes;
        this.layouts = layouts;
        this.questions = questions;
        this.categories = categories;
        this.access = access;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    public LayoutView get(UUID itemId) {
        CurrentUser user = currentUser.require();
        QuizItem quiz = requireManage(user, itemId);
        return view(user.tenantId(), layouts.find(user.tenantId(), quiz.id()).orElse(QuizLayout.empty()));
    }

    @Transactional
    public LayoutView replace(UUID itemId, List<LayoutSlot> slots) {
        CurrentUser user = currentUser.require();
        QuizItem quiz = requireManage(user, itemId);
        QuizLayout layout = QuizLayout.of(slots);
        validateReferences(user.tenantId(), quiz.courseId(), layout);
        layouts.save(user.tenantId(), quiz.courseId(), quiz.id(), layout);
        audit.record(AuditRecord.of(user.tenantId(), user.userId(), "quiz.layout_changed", "item", itemId.toString())
                .withDiff(Map.of("slots", layout.slots().size())));
        return view(user.tenantId(), layout);
    }

    private QuizItem requireManage(CurrentUser user, UUID itemId) {
        QuizItem quiz = quizzes.require(user.tenantId(), itemId);
        access.require(Permission.QUIZ_MANAGE, quiz.context());
        return quiz;
    }

    private void validateReferences(UUID tenantId, UUID courseId, QuizLayout layout) {
        Map<UUID, StoredQuestion> found = questions.findAll(tenantId, layout.fixedQuestionIds());
        List<FieldViolation> violations = new ArrayList<>();
        for (int i = 0; i < layout.slots().size(); i++) {
            String field = "slots[" + i + "]";
            switch (layout.slots().get(i)) {
                case LayoutSlot.Fixed fixed -> {
                    StoredQuestion question = found.get(fixed.questionId());
                    if (question == null || !question.courseId().equals(courseId)) {
                        violations.add(new FieldViolation(field + ".questionId", "not_found", "Question not found"));
                    }
                }
                case LayoutSlot.Random random -> {
                    if (random.categoryId() != null && !categoryInCourse(tenantId, courseId, random.categoryId())) {
                        violations.add(new FieldViolation(field + ".random.categoryId", "not_found", "Category not found"));
                    }
                }
            }
        }
        if (!violations.isEmpty()) {
            throw new ValidationException(violations);
        }
    }

    private boolean categoryInCourse(UUID tenantId, UUID courseId, UUID categoryId) {
        return categories.find(tenantId, categoryId).filter(c -> c.courseId().equals(courseId)).isPresent();
    }

    private LayoutView view(UUID tenantId, QuizLayout layout) {
        Map<UUID, StoredQuestion> found = questions.findAll(tenantId, layout.fixedQuestionIds());
        Map<UUID, Integer> usage = layouts.usage(tenantId, found.keySet());
        List<SlotView> slots = layout.slots().stream().map(QuizLayoutService::slotView).toList();
        List<QuestionSummaryView> summaries = found.values().stream()
                .map(q -> new QuestionSummaryView(q.id(), q.type().key(), q.title(), q.tags(), q.currentVersion(), q.updatedAt(),
                        usage.getOrDefault(q.id(), 0)))
                .toList();
        return new LayoutView(slots, summaries);
    }

    private static SlotView slotView(LayoutSlot slot) {
        return switch (slot) {
            case LayoutSlot.Fixed fixed -> new SlotView(fixed.questionId(), null, fixed.points(), fixed.page());
            case LayoutSlot.Random random -> new SlotView(null, new RandomView(random.categoryId(), random.tag(), random.count()),
                    random.points(), random.page());
        };
    }
}
