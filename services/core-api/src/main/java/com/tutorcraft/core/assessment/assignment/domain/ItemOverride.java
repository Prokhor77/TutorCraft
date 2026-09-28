package com.tutorcraft.core.assessment.assignment.domain;

import java.time.Instant;
import java.util.UUID;

/** Индивидуальное или групповое продление срока (FR-ASSIGN-03). Ровно одно из userId/groupId задано. */
public record ItemOverride(UUID id, UUID tenantId, UUID courseId, UUID itemId, UUID userId, UUID groupId,
                           Instant dueAt, Instant closeAt, UUID createdBy, Instant createdAt) {

    public boolean forUser() {
        return userId != null;
    }
}
