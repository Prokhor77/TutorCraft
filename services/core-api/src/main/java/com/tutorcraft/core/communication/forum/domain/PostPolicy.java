package com.tutorcraft.core.communication.forum.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Правила правки и удаления (FR-FORUM-04): модератор — всегда; автор — в пределах окна правки,
 * а удалить может только пост без ответов.
 */
public final class PostPolicy {

    private PostPolicy() {
    }

    public static boolean canEdit(ForumPost post, UUID viewerId, boolean moderator, Instant now, Duration window) {
        return moderator || (post.authorId().equals(viewerId) && withinWindow(post, now, window));
    }

    public static boolean canDelete(ForumPost post, UUID viewerId, boolean moderator, Instant now, Duration window,
                                    boolean hasReplies) {
        return moderator || (post.authorId().equals(viewerId) && withinWindow(post, now, window) && !hasReplies);
    }

    private static boolean withinWindow(ForumPost post, Instant now, Duration window) {
        return now.isBefore(post.createdAt().plus(window));
    }
}
