package com.tutorcraft.core.communication.forum.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Пост форума (таблица forum_posts). Корневой пост темы: {@code parentId == null}, глубина 0. */
public record ForumPost(UUID id, UUID tenantId, UUID courseId, UUID itemId, UUID discussionId, UUID parentId, int depth,
                        UUID authorId, Map<String, Object> body, boolean hidden, Instant createdAt, Instant editedAt) {

    public boolean isRoot() {
        return parentId == null;
    }
}
