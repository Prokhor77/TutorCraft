package com.tutorcraft.core.assessment.quiz.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.assessment.quiz.application.QuizItems.QuizItem;
import com.tutorcraft.core.assessment.quiz.application.QuizViews.RegradeView;
import com.tutorcraft.core.assessment.quiz.domain.Attempt;
import com.tutorcraft.core.assessment.quiz.domain.AttemptScoring.AttemptScore;
import com.tutorcraft.core.assessment.quiz.domain.AttemptSlot;
import com.tutorcraft.core.assessment.quiz.domain.StoredQuestion;
import com.tutorcraft.core.audit.AuditLog;
import com.tutorcraft.core.audit.AuditRecord;
import com.tutorcraft.core.gradebook.GradebookApi;
import com.tutorcraft.core.gradebook.GradebookApi.GradeView;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Переоценка после исправления ключа (AC-5, FR-QUIZ-07): завершённые попытки переводятся на текущие версии вопросов,
 * автопроверка повторяется (ручные оценки эссе сохраняются), итоговые оценки перезаписываются в журнал.
 * GradeHistory и уведомление студента об изменившейся опубликованной оценке (grade_published) обеспечивает gradebook.
 */
@Service
public class QuizRegradeService {

    private static final Logger log = LoggerFactory.getLogger(QuizRegradeService.class);

    private final QuizItems quizzes;
    private final AttemptRepository attempts;
    private final QuestionRepository questions;
    private final AttemptFinisher finisher;
    private final QuizGradeSync gradeSync;
    private final GradebookApi gradebook;
    private final AccessService access;
    private final CurrentUserProvider currentUser;
    private final AuditLog audit;
    private final Clock clock;

    public QuizRegradeService(QuizItems quizzes, AttemptRepository attempts, QuestionRepository questions,
                              AttemptFinisher finisher, QuizGradeSync gradeSync, GradebookApi gradebook,
                              AccessService access, CurrentUserProvider currentUser, AuditLog audit, Clock clock) {
        this.quizzes = quizzes;
        this.attempts = attempts;
        this.questions = questions;
        this.finisher = finisher;
        this.gradeSync = gradeSync;
        this.gradebook = gradebook;
        this.access = access;
        this.currentUser = currentUser;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public RegradeView regrade(UUID itemId) {
        CurrentUser user = currentUser.require();
        QuizItem quiz = quizzes.require(user.tenantId(), itemId);
        access.require(Permission.QUIZ_MANAGE, quiz.context());
        List<Attempt> finished = attempts.finishedOfItem(user.tenantId(), itemId);
        Map<UUID, UUID> currentVersions = currentVersions(user.tenantId(), finished);
        Instant now = clock.instant();
        finished.forEach(attempt -> regradeAttempt(attempt, currentVersions, now));
        Set<UUID> learners = finished.stream().map(Attempt::userId).collect(Collectors.toCollection(LinkedHashSet::new));
        long changed = learners.stream().filter(learner -> resync(quiz, learner, user.userId())).count();
        audit.record(AuditRecord.of(user.tenantId(), user.userId(), "quiz.regraded", "item", itemId.toString())
                .withDiff(Map.of("attempts", finished.size(), "gradesChanged", changed)));
        log.info("Quiz {} regraded: {} attempts, {} grades changed", itemId, finished.size(), changed);
        return new RegradeView(finished.size());
    }

    private Map<UUID, UUID> currentVersions(UUID tenantId, List<Attempt> finished) {
        Set<UUID> questionIds = finished.stream().flatMap(attempt -> attempt.layout().stream())
                .map(AttemptSlot::questionId).collect(Collectors.toSet());
        Map<UUID, UUID> versions = new HashMap<>();
        questions.findAll(tenantId, questionIds).values()
                .forEach((StoredQuestion question) -> versions.put(question.id(), question.currentVersionId()));
        return versions;
    }

    private void regradeAttempt(Attempt attempt, Map<UUID, UUID> currentVersions, Instant now) {
        Map<Integer, UUID> overrides = new HashMap<>();
        attempt.layout().forEach(slot -> Optional.ofNullable(currentVersions.get(slot.questionId()))
                .ifPresent(versionId -> overrides.put(slot.slot(), versionId)));
        AttemptScore total = finisher.regradeAnswers(attempt, overrides, true, now);
        attempts.updateScore(attempt.tenantId(), attempt.id(), total.score(), total.needsManualGrading());
    }

    /** @return true — итоговая оценка студента изменилась */
    private boolean resync(QuizItem quiz, UUID learnerId, UUID actorId) {
        UUID tenantId = quiz.item().tenantId();
        Optional<BigDecimal> before = gradebook.grade(tenantId, quiz.id(), learnerId).map(GradeView::score);
        Optional<BigDecimal> after = gradeSync.sync(tenantId, quiz.id(), learnerId, actorId);
        return after.isPresent() && (before.isEmpty() || before.get().compareTo(after.get()) != 0);
    }
}
