package com.tutorcraft.core.assessment.assignment.domain;

import com.tutorcraft.core.shared.domain.BusinessRuleException;
import java.time.Instant;
import java.util.Map;

/**
 * Правила жизненного цикла сдачи (FR-ASSIGN-02/04, AC-3): окно сдачи, опоздание, правка черновика, попытки.
 * Граница closeAt включительна: сдать ровно в момент закрытия можно, позже — нет.
 */
public final class SubmissionRules {

    private SubmissionRules() {
    }

    public static void requireWindowOpen(EffectiveDeadlines deadlines, Instant now) {
        if (deadlines.openAt() != null && now.isBefore(deadlines.openAt())) {
            throw new BusinessRuleException(AssignmentErrors.NOT_OPEN, "Assignment is not open yet",
                    Map.of("openAt", deadlines.openAt().toString()));
        }
        if (deadlines.closeAt() != null && now.isAfter(deadlines.closeAt())) {
            throw new BusinessRuleException(AssignmentErrors.CLOSED, "Assignment is closed",
                    Map.of("closeAt", deadlines.closeAt().toString()));
        }
    }

    public static boolean isLate(EffectiveDeadlines deadlines, Instant submittedAt) {
        return deadlines.dueAt() != null && submittedAt.isAfter(deadlines.dueAt());
    }

    /** Черновик правится всегда; отправленная работа — только если кнопка «Отправить» не требуется и она не проверена. */
    public static void requireEditable(Submission submission, AssignmentSettings settings) {
        boolean draft = submission.status() == SubmissionStatus.DRAFT;
        boolean autoSubmitted = !settings.requireSubmitButton() && submission.status().awaitsGrading();
        if (!draft && !autoSubmitted) {
            throw new BusinessRuleException(AssignmentErrors.NOT_EDITABLE, "Submission can no longer be edited");
        }
    }

    public static void requireSubmittable(Submission submission, AssignmentSettings settings) {
        if (!settings.submissionType().acceptsOnlineSubmission()) {
            throw new BusinessRuleException(AssignmentErrors.OFFLINE, "Assignment does not accept online submissions");
        }
        if (submission.status() != SubmissionStatus.DRAFT) {
            throw new BusinessRuleException(AssignmentErrors.ALREADY_SUBMITTED, "Submission has already been submitted");
        }
        if (!submission.hasContent()) {
            throw new BusinessRuleException(AssignmentErrors.EMPTY_SUBMISSION, "Add a file or text before submitting");
        }
    }

    /** Новая попытка начинается, когда текущая возвращена на доработку. */
    public static boolean needsNewAttempt(Submission current) {
        return current.status() == SubmissionStatus.RETURNED;
    }

    public static void requireAttemptAvailable(AssignmentSettings settings, int usedAttempts) {
        if (settings.maxAttempts() != null && usedAttempts >= settings.maxAttempts()) {
            throw new BusinessRuleException(AssignmentErrors.ATTEMPTS_EXHAUSTED, "No attempts left",
                    Map.of("maxAttempts", settings.maxAttempts()));
        }
    }
}
