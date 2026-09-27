package com.tutorcraft.core.assessment;

import java.util.UUID;

public final class AssessmentEvents {

    private AssessmentEvents() {
    }

    public record SubmissionSubmitted(UUID tenantId, UUID courseId, UUID itemId, UUID userId, UUID submissionId, boolean late) {
    }

    public record AttemptFinished(UUID tenantId, UUID courseId, UUID itemId, UUID userId, UUID attemptId) {
    }
}
