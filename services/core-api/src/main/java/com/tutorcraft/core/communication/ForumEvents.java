package com.tutorcraft.core.communication;

import java.util.UUID;

public final class ForumEvents {

    private ForumEvents() {
    }

    public record PostCreated(UUID tenantId, UUID courseId, UUID itemId, UUID discussionId, UUID postId, UUID authorId) {
    }
}
