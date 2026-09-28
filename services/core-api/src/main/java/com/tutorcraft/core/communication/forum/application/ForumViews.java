package com.tutorcraft.core.communication.forum.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Представления форумов (контракт §11). */
public final class ForumViews {

    private ForumViews() {
    }

    public record DiscussionView(UUID id, String title, UUID authorId, String authorName, boolean pinned, boolean locked,
                                 boolean subscribed, int replyCount, int unreadCount, Instant lastPostAt, Instant createdAt) {
    }

    public record PostView(UUID id, UUID parentId, UUID authorId, String authorName, Map<String, Object> body,
                           Instant createdAt, Instant editedAt, boolean canEdit, boolean canDelete, boolean hidden,
                           List<PostView> children) {
    }

    public record DiscussionDetailView(DiscussionView discussion, List<PostView> posts) {
    }
}
