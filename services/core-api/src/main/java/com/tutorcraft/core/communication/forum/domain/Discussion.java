package com.tutorcraft.core.communication.forum.domain;

import java.time.Instant;
import java.util.UUID;

/** Тема форума (таблица forum_discussions). */
public record Discussion(UUID id, UUID tenantId, UUID courseId, UUID itemId, UUID authorId, String title, boolean pinned,
                         boolean locked, int replyCount, Instant lastPostAt, Instant createdAt) {
}
