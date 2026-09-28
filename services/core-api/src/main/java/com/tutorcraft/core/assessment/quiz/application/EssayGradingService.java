package com.tutorcraft.core.assessment.quiz.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.assessment.quiz.application.AttemptRepository.AnswerGrade;
import com.tutorcraft.core.assessment.quiz.application.QuizViews.AttemptResultView;
import com.tutorcraft.core.assessment.quiz.domain.AnswerRecord;
import com.tutorcraft.core.assessment.quiz.domain.Attempt;
import com.tutorcraft.core.assessment.quiz.domain.AttemptScoring;
import com.tutorcraft.core.assessment.quiz.domain.AttemptScoring.AttemptScore;
import com.tutorcraft.core.assessment.quiz.domain.AttemptSlot;
import com.tutorcraft.core.assessment.AssessmentEvents.EssayGraded;
import com.tutorcraft.core.audit.AuditLog;
import com.tutorcraft.core.audit.AuditRecord;
import com.tutorcraft.core.shared.domain.BusinessRuleException;
import com.tutorcraft.core.shared.domain.ConflictException;
import com.tutorcraft.core.shared.domain.NotFoundException;
import com.tutorcraft.core.shared.domain.Validator;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ручная проверка эссе из единой очереди (FR-QUIZ-04, FR-GRADE-06). Требует {@code submission.grade}. */
@Service
public class EssayGradingService {

    private static final Logger log = LoggerFactory.getLogger(EssayGradingService.class);
    private static final int MAX_COMMENT = 5000;
    private static final int FRACTION_SCALE = 6;

    private final AttemptRepository attempts;
    private final AttemptFinisher finisher;
    private final QuizGradeSync gradeSync;
    private final AttemptResultService results;
    private final AccessService access;
    private final CurrentUserProvider currentUser;
    private final AuditLog audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public EssayGradingService(AttemptRepository attempts, AttemptFinisher finisher, QuizGradeSync gradeSync,
                               AttemptResultService results, AccessService access, CurrentUserProvider currentUser,
                               AuditLog audit, ApplicationEventPublisher events, Clock clock) {
        this.attempts = attempts;
        this.finisher = finisher;
        this.gradeSync = gradeSync;
        this.results = results;
        this.access = access;
        this.currentUser = currentUser;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public AttemptResultView grade(UUID attemptId, int slotNumber, BigDecimal score, String comment) {
        CurrentUser user = currentUser.require();
        Attempt attempt = attempts.lock(user.tenantId(), attemptId).orElseThrow(AttemptService::notFound);
        access.require(Permission.SUBMISSION_GRADE, AccessContext.course(attempt.courseId()));
        if (attempt.inProgress()) {
            throw new ConflictException(QuizErrors.ATTEMPT_IN_PROGRESS, "Attempt is not finished yet");
        }
        AttemptSlot slot = attempt.slot(slotNumber).orElseThrow(EssayGradingService::slotNotFound);
        AnswerRecord answer = attempts.answer(user.tenantId(), attemptId, slotNumber).orElseThrow(EssayGradingService::slotNotFound);
        if (!answer.needsManual()) {
            throw new BusinessRuleException(QuizErrors.NOT_MANUAL, "This question is graded automatically");
        }
        validate(score, comment, slot.points());
        attempts.updateAnswerGrade(user.tenantId(), new AnswerGrade(attemptId, slotNumber, answer.questionVersionId(),
                fraction(score, slot.points()), score.setScale(AttemptScoring.SCALE, RoundingMode.HALF_UP), true,
                user.userId(), comment, clock.instant()));
        AttemptScore total = finisher.storedTotal(attempt);
        attempts.updateScore(user.tenantId(), attemptId, total.score(), total.needsManualGrading());
        gradeSync.sync(user.tenantId(), attempt.itemId(), attempt.userId(), user.userId());
        audit.record(AuditRecord.of(user.tenantId(), user.userId(), "quiz.essay_graded", "attempt", attemptId.toString())
                .withDiff(Map.of("slot", slotNumber)));
        events.publishEvent(new EssayGraded(user.tenantId(), attempt.courseId(), attempt.itemId(), attemptId, user.userId()));
        log.info("Essay slot {} of attempt {} graded", slotNumber, attemptId);
        return results.fullResult(attempts.find(user.tenantId(), attemptId).orElseThrow());
    }

    private static void validate(BigDecimal score, String comment, BigDecimal points) {
        new Validator()
            .check(score != null && score.signum() >= 0 && score.compareTo(points) <= 0, "score", "out_of_range",
                    "Score must be between 0 and " + points)
            .maxLength(comment, MAX_COMMENT, "comment")
            .throwIfInvalid();
    }

    private static double fraction(BigDecimal score, BigDecimal points) {
        return points.signum() == 0 ? 0 : score.divide(points, FRACTION_SCALE, RoundingMode.HALF_UP).doubleValue();
    }

    private static NotFoundException slotNotFound() {
        return new NotFoundException(QuizErrors.SLOT_NOT_FOUND, "Question slot not found");
    }
}
