package com.tutorcraft.core.communication.forum.application;

import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.audit.AuditLog;
import com.tutorcraft.core.audit.AuditRecord;
import com.tutorcraft.core.communication.forum.application.ForumItems.ForumContext;
import com.tutorcraft.core.communication.forum.application.ForumViews.PostView;
import com.tutorcraft.core.communication.forum.domain.Discussion;
import com.tutorcraft.core.communication.forum.domain.ForumPost;
import com.tutorcraft.core.communication.forum.domain.ForumType;
import com.tutorcraft.core.communication.forum.domain.PostPolicy;
import com.tutorcraft.core.communication.forum.domain.PostVisibility;
import com.tutorcraft.core.communication.forum.domain.ReplyPlacement;
import com.tutorcraft.core.shared.domain.BusinessRuleException;
import com.tutorcraft.core.shared.domain.Ids;
import com.tutorcraft.core.shared.domain.NotFoundException;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ответы в темах: создание (дерево ≤ 3 уровней), правка в окне, удаление, скрытие модератором (FR-FORUM-01/04). */
@Service
public class PostService {

    private static final Logger log = LoggerFactory.getLogger(PostService.class);

    private final ForumItems forums;
    private final ForumRepository repository;
    private final ForumContent content;
    private final ForumNotifier notifier;
    private final ForumViewAssembler views;
    private final ForumPostEffects effects;
    private final CurrentUserProvider currentUser;
    private final AuditLog audit;
    private final Clock clock;

    public PostService(ForumItems forums, ForumRepository repository, ForumContent content, ForumNotifier notifier,
                       ForumViewAssembler views, ForumPostEffects effects, CurrentUserProvider currentUser, AuditLog audit,
                       Clock clock) {
        this.forums = forums;
        this.repository = repository;
        this.content = content;
        this.notifier = notifier;
        this.views = views;
        this.effects = effects;
        this.currentUser = currentUser;
        this.audit = audit;
        this.clock = clock;
    }

    /** @param parentId null — ответ на корневой пост темы */
    @Transactional
    public PostView reply(UUID discussionId, UUID parentId, Map<String, Object> body, List<UUID> mentions) {
        CurrentUser user = currentUser.require();
        Discussion discussion = repository.findDiscussion(user.tenantId(), discussionId)
                .orElseThrow(ForumPostEffects::discussionNotFound);
        ForumContext forum = forums.requireReadable(user.tenantId(), user.userId(), discussion.itemId());
        requireCanReply(forum, discussion);
        ForumPost parent = visibleParent(forum, discussion, parentId);
        Set<UUID> mentioned = notifier.resolveMentions(forum, mentions);
        Instant now = clock.instant();
        ReplyPlacement placement = ReplyPlacement.under(parent);
        UUID postId = Ids.newId();
        ForumPost post = new ForumPost(postId, user.tenantId(), forum.courseId(), forum.item().id(), discussionId,
                placement.parentId(), placement.depth(), user.userId(), content.sanitizeAndLink(user.tenantId(), body, postId),
                false, now, null);
        repository.insertPost(post);
        repository.touchDiscussion(user.tenantId(), discussionId, now, 1);
        Set<UUID> subscribers = repository.subscribers(user.tenantId(), discussionId);
        effects.afterPost(forum, discussion, post, now);
        String authorName = views.names(user.tenantId(), List.of(user.userId())).get(user.userId());
        notifier.reply(forum, discussion, post, subscribers, mentioned, authorName);
        log.info("Post {} added to discussion {} at depth {}", postId, discussionId, placement.depth());
        return views.post(forum, post, authorName, false, now, List.of());
    }

    @Transactional
    public PostView edit(UUID postId, Map<String, Object> body) {
        CurrentUser user = currentUser.require();
        ForumPost post = requirePost(user, postId);
        ForumContext forum = forums.requireReadable(user.tenantId(), user.userId(), post.itemId());
        Instant now = clock.instant();
        if (!PostPolicy.canEdit(post, user.userId(), forum.moderator(), now, forum.settings().editWindow())) {
            throw notAllowed(post, user, ForumErrors.EDIT_WINDOW_PASSED);
        }
        Map<String, Object> sanitized = content.sanitizeAndLink(user.tenantId(), body, postId);
        repository.updateBody(user.tenantId(), postId, sanitized, now);
        ForumPost updated = new ForumPost(post.id(), post.tenantId(), post.courseId(), post.itemId(), post.discussionId(),
                post.parentId(), post.depth(), post.authorId(), sanitized, post.hidden(), post.createdAt(), now);
        String authorName = views.names(user.tenantId(), List.of(post.authorId())).get(post.authorId());
        return views.post(forum, updated, authorName, repository.hasReplies(user.tenantId(), postId), now, List.of());
    }

    /** Удаляет пост с ветвью; удаление корневого поста удаляет тему. */
    @Transactional
    public void delete(UUID postId) {
        CurrentUser user = currentUser.require();
        ForumPost post = requirePost(user, postId);
        ForumContext forum = forums.requireReadable(user.tenantId(), user.userId(), post.itemId());
        Instant now = clock.instant();
        boolean hasReplies = repository.hasReplies(user.tenantId(), postId);
        if (!PostPolicy.canDelete(post, user.userId(), forum.moderator(), now, forum.settings().editWindow(), hasReplies)) {
            throw notAllowed(post, user, ForumErrors.CANNOT_DELETE);
        }
        int removed = repository.deleteSubtree(user.tenantId(), postId, now);
        if (post.isRoot()) {
            repository.deleteDiscussion(user.tenantId(), post.discussionId(), now);
        } else {
            repository.touchDiscussion(user.tenantId(), post.discussionId(), null, -removed);
        }
        if (!post.authorId().equals(user.userId())) {
            audit.record(AuditRecord.of(user.tenantId(), user.userId(), "forum.post_deleted", "post", postId.toString())
                    .withDiff(Map.of("removed", removed)));
        }
    }

    @Transactional
    public void setHidden(UUID postId, boolean hidden) {
        CurrentUser user = currentUser.require();
        ForumPost post = requirePost(user, postId);
        ForumContext forum = forums.requireReadable(user.tenantId(), user.userId(), post.itemId());
        forum.require(Permission.FORUM_MODERATE);
        repository.setHidden(user.tenantId(), postId, hidden);
        audit.record(AuditRecord.of(user.tenantId(), user.userId(), hidden ? "forum.post_hidden" : "forum.post_unhidden",
                "post", postId.toString()));
    }

    private static void requireCanReply(ForumContext forum, Discussion discussion) {
        boolean announcements = forum.settings().forumType() == ForumType.ANNOUNCEMENTS;
        forum.require(announcements ? Permission.FORUM_ANNOUNCE : Permission.FORUM_POST);
        if (discussion.locked() && !forum.moderator()) {
            throw new BusinessRuleException(ForumErrors.LOCKED, "Discussion is locked");
        }
    }

    /** Родитель должен быть виден отвечающему (скрытые посты, правило Q&amp;A). */
    private ForumPost visibleParent(ForumContext forum, Discussion discussion, UUID parentId) {
        List<ForumPost> visible = PostVisibility.visible(repository.posts(forum.tenantId(), discussion.id()),
                forum.settings().forumType(), forum.userId(), forum.moderator());
        return visible.stream()
                .filter(post -> parentId == null ? post.isRoot() : post.id().equals(parentId))
                .findFirst().orElseThrow(PostService::postNotFound);
    }

    private ForumPost requirePost(CurrentUser user, UUID postId) {
        return repository.findPost(user.tenantId(), postId).orElseThrow(PostService::postNotFound);
    }

    private static RuntimeException notAllowed(ForumPost post, CurrentUser user, String code) {
        if (!post.authorId().equals(user.userId())) {
            return ForumItems.denied(Permission.FORUM_MODERATE);
        }
        return new BusinessRuleException(code, "Action is no longer allowed for this post");
    }

    private static NotFoundException postNotFound() {
        return new NotFoundException(ForumErrors.POST_NOT_FOUND, "Post not found");
    }
}
