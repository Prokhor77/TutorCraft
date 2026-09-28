package com.tutorcraft.core.communication.forum.domain;

import java.util.UUID;

/**
 * Место нового ответа в дереве (FR-FORUM-01): глубина не больше {@link #MAX_DEPTH}; ответ на пост максимальной
 * глубины становится его «соседом» (прикрепляется к родителю этого поста) — дальше обсуждение идёт плоско.
 */
public record ReplyPlacement(UUID parentId, int depth) {

    public static final int MAX_DEPTH = 3;

    public static ReplyPlacement under(ForumPost parent) {
        if (parent.depth() >= MAX_DEPTH && parent.parentId() != null) {
            return new ReplyPlacement(parent.parentId(), MAX_DEPTH);
        }
        return new ReplyPlacement(parent.id(), parent.depth() + 1);
    }
}
