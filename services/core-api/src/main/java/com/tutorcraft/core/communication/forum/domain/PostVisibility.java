package com.tutorcraft.core.communication.forum.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Какие посты темы видит пользователь:
 * <ul>
 *   <li>скрытые модератором посты (и их ветви) — только модераторам;</li>
 *   <li>Q&amp;A (FR-FORUM-02): студент видит чужие ответы только после своего поста в теме —
 *       до этого ему видны корневой пост и собственные посты.</li>
 * </ul>
 * Ветвь, родитель которой не виден, не показывается. Посты ожидаются в порядке создания.
 */
public final class PostVisibility {

    private PostVisibility() {
    }

    public static List<ForumPost> visible(List<ForumPost> posts, ForumType type, UUID viewerId, boolean moderator) {
        boolean qaRestricted = type == ForumType.QA && !moderator && !hasOwnReply(posts, viewerId);
        Set<UUID> shown = new HashSet<>();
        return posts.stream().filter(post -> {
            boolean parentShown = post.parentId() == null || shown.contains(post.parentId());
            boolean allowed = parentShown && (moderator || !post.hidden())
                    && (!qaRestricted || post.isRoot() || post.authorId().equals(viewerId));
            if (allowed) {
                shown.add(post.id());
            }
            return allowed;
        }).toList();
    }

    /** Студент «ответил» в Q&amp;A, если у него есть видимый (не скрытый) пост-ответ или он автор темы. */
    static boolean hasOwnReply(List<ForumPost> posts, UUID viewerId) {
        return posts.stream().anyMatch(post -> post.authorId().equals(viewerId) && !post.hidden());
    }
}
