package com.tutorcraft.core.communication.forum.application;

import com.tutorcraft.core.dashboard.spi.RecentPostsSource;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Последние посты форумов для главной преподавателя (FR-DASH-02). Зависит только от репозитория форумов. */
@Component
class RecentForumPosts implements RecentPostsSource {

    private final ForumRepository repository;

    RecentForumPosts(ForumRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<RecentPost> recent(UUID tenantId, Collection<UUID> courseIds, int limit) {
        if (courseIds.isEmpty() || limit <= 0) {
            return List.of();
        }
        return repository.recentPosts(tenantId, courseIds, limit).stream()
                .map(row -> new RecentPost(row.discussionId(), row.courseId(), row.discussionTitle(), row.authorId(),
                        row.createdAt()))
                .toList();
    }
}
