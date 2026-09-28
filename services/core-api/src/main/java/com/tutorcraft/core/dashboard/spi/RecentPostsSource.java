package com.tutorcraft.core.dashboard.spi;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Последние посты форумов для главной преподавателя (FR-DASH-02). Реализует communication.forum;
 * пока реализации нет, блок пуст. Реализация зависит только от своих репозиториев.
 */
public interface RecentPostsSource {

    /** Новые первыми. */
    List<RecentPost> recent(UUID tenantId, Collection<UUID> courseIds, int limit);

    record RecentPost(UUID discussionId, UUID courseId, String title, UUID authorId, Instant createdAt) {
    }
}
