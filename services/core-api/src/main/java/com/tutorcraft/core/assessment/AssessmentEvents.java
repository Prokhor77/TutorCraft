package com.tutorcraft.core.assessment;

import java.util.UUID;

public final class AssessmentEvents {

    private AssessmentEvents() {
    }

    public record SubmissionSubmitted(UUID tenantId, UUID courseId, UUID itemId, UUID userId, UUID submissionId, boolean late) {
    }

    public record AttemptFinished(UUID tenantId, UUID courseId, UUID itemId, UUID userId, UUID attemptId) {
    }

    /** Проверяющий оценил сдачу или вернул её на доработку (сдача ушла из очереди проверки). */
    public record SubmissionGraded(UUID tenantId, UUID courseId, UUID itemId, UUID submissionId, UUID graderId) {
    }

    /** Проверяющий оценил эссе в попытке теста. */
    public record EssayGraded(UUID tenantId, UUID courseId, UUID itemId, UUID attemptId, UUID graderId) {
    }
}
