package com.tutorcraft.core.progress;

import java.util.UUID;

public final class ProgressEvents {

    private ProgressEvents() {
    }

    public record CourseCompleted(UUID tenantId, UUID courseId, UUID userId) {
    }

    public record ItemCompletionChanged(UUID tenantId, UUID courseId, UUID itemId, UUID userId, boolean complete) {
    }
}
