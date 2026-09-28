package com.tutorcraft.core.communication.forum.application;

import com.tutorcraft.core.communication.forum.domain.Discussion;
import com.tutorcraft.core.communication.forum.domain.ForumPost;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.api.PageResponse;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Форумы (PostgreSQL: forum_discussions, forum_posts, forum_subscriptions, forum_reads). Удалённое не возвращается. */
public interface ForumRepository {

    void insertDiscussion(Discussion discussion);

    Optional<Discussion> findDiscussion(UUID tenantId, UUID discussionId);

    List<Discussion> pinned(UUID tenantId, UUID itemId);

    PageResponse<Discussion> pageUnpinned(UUID tenantId, UUID itemId, PageQuery page);

    void setPinned(UUID tenantId, UUID discussionId, boolean pinned);

    void setLocked(UUID tenantId, UUID discussionId, boolean locked);

    /** Обновляет last_post_at и счётчик ответов (delta может быть отрицательной). */
    void touchDiscussion(UUID tenantId, UUID discussionId, Instant lastPostAt, int replyDelta);

    void deleteDiscussion(UUID tenantId, UUID discussionId, Instant at);

    void insertPost(ForumPost post);

    Optional<ForumPost> findPost(UUID tenantId, UUID postId);

    /** Посты темы в порядке создания. */
    List<ForumPost> posts(UUID tenantId, UUID discussionId);

    boolean hasReplies(UUID tenantId, UUID postId);

    void updateBody(UUID tenantId, UUID postId, Map<String, Object> body, Instant editedAt);

    void setHidden(UUID tenantId, UUID postId, boolean hidden);

    /** Мягко удаляет пост и всю его ветвь. @return число удалённых постов */
    int deleteSubtree(UUID tenantId, UUID postId, Instant at);

    void subscribe(UUID tenantId, UUID discussionId, UUID userId, Instant at);

    void unsubscribe(UUID tenantId, UUID discussionId, UUID userId);

    Set<UUID> subscribers(UUID tenantId, UUID discussionId);

    void markRead(UUID tenantId, UUID discussionId, UUID userId, Instant at);

    /** Для страницы тем: подписки и непрочитанные посты пользователя. */
    Map<UUID, ReaderState> readerStates(UUID tenantId, UUID userId, Collection<UUID> discussionIds);

    /** Последние видимые посты в курсах, новые первыми (главная преподавателя, FR-DASH-02). */
    List<RecentPostRow> recentPosts(UUID tenantId, Collection<UUID> courseIds, int limit);

    record RecentPostRow(UUID discussionId, UUID courseId, String discussionTitle, UUID authorId, Instant createdAt) {
    }

    record ReaderState(boolean subscribed, int unreadCount) {

        public static final ReaderState NONE = new ReaderState(false, 0);
    }
}
