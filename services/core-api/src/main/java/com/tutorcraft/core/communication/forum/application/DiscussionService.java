package com.tutorcraft.core.communication.forum.application;

import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.audit.AuditLog;
import com.tutorcraft.core.audit.AuditRecord;
import com.tutorcraft.core.communication.forum.application.ForumItems.ForumContext;
import com.tutorcraft.core.communication.forum.application.ForumRepository.ReaderState;
import com.tutorcraft.core.communication.forum.application.ForumViews.DiscussionDetailView;
import com.tutorcraft.core.communication.forum.application.ForumViews.DiscussionView;
import com.tutorcraft.core.communication.forum.domain.Discussion;
import com.tutorcraft.core.communication.forum.domain.ForumPost;
import com.tutorcraft.core.communication.forum.domain.ForumType;
import com.tutorcraft.core.communication.forum.domain.PostVisibility;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.api.PageResponse;
import com.tutorcraft.core.shared.domain.Ids;
import com.tutorcraft.core.shared.domain.Validator;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Темы форума: список, создание, просмотр дерева, закрепление, блокировка, подписка, прочтение (FR-FORUM-01..03). */
@Service
public class DiscussionService {

    private static final Logger log = LoggerFactory.getLogger(DiscussionService.class);
    private static final int MAX_TITLE = 255;

    private final ForumItems forums;
    private final ForumRepository repository;
    private final ForumContent content;
    private final ForumNotifier notifier;
    private final ForumViewAssembler views;
    private final ForumPostEffects effects;
    private final CurrentUserProvider currentUser;
    private final AuditLog audit;
    private final Clock clock;

    public DiscussionService(ForumItems forums, ForumRepository repository, ForumContent content, ForumNotifier notifier,
                             ForumViewAssembler views, ForumPostEffects effects, CurrentUserProvider currentUser,
                             AuditLog audit, Clock clock) {
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

    /** Первая страница начинается с закреплённых тем; далее — по последнему посту. */
    public PageResponse<DiscussionView> list(UUID itemId, PageQuery page) {
        CurrentUser user = currentUser.require();
        forums.requireReadable(user.tenantId(), user.userId(), itemId);
        PageResponse<Discussion> unpinned = repository.pageUnpinned(user.tenantId(), itemId, page);
        List<Discussion> discussions = new ArrayList<>(page.after().isEmpty() ? repository.pinned(user.tenantId(), itemId) : List.of());
        discussions.addAll(unpinned.items());
        return new PageResponse<>(toViews(user, discussions), unpinned.nextCursor());
    }

    @Transactional
    public DiscussionView create(UUID itemId, String title, Map<String, Object> body, List<UUID> mentions) {
        CurrentUser user = currentUser.require();
        ForumContext forum = forums.requireReadable(user.tenantId(), user.userId(), itemId);
        forum.require(forum.settings().forumType() == ForumType.ANNOUNCEMENTS ? Permission.FORUM_ANNOUNCE : Permission.FORUM_POST);
        new Validator().notBlank(title, "title").maxLength(title, MAX_TITLE, "title").throwIfInvalid();
        Set<UUID> mentioned = notifier.resolveMentions(forum, mentions);
        Instant now = clock.instant();
        Discussion discussion = new Discussion(Ids.newId(), user.tenantId(), forum.courseId(), itemId, user.userId(),
                title.strip(), false, false, 0, now, now);
        UUID rootId = Ids.newId();
        ForumPost root = new ForumPost(rootId, user.tenantId(), forum.courseId(), itemId, discussion.id(), null, 0,
                user.userId(), content.sanitizeAndLink(user.tenantId(), body, rootId), false, now, null);
        repository.insertDiscussion(discussion);
        repository.insertPost(root);
        effects.afterPost(forum, discussion, root, now);
        String authorName = views.names(user.tenantId(), List.of(user.userId())).get(user.userId());
        notifyCreated(forum, discussion, root, mentioned, authorName);
        log.info("Discussion {} created in forum {}", discussion.id(), itemId);
        return views.discussion(discussion, authorName, new ReaderState(true, 0));
    }

    public DiscussionDetailView get(UUID discussionId) {
        CurrentUser user = currentUser.require();
        Discussion discussion = requireDiscussion(user.tenantId(), discussionId);
        ForumContext forum = forums.requireReadable(user.tenantId(), user.userId(), discussion.itemId());
        List<ForumPost> posts = repository.posts(user.tenantId(), discussionId);
        List<ForumPost> visible = PostVisibility.visible(posts, forum.settings().forumType(), user.userId(), forum.moderator());
        Set<UUID> withReplies = posts.stream().map(ForumPost::parentId).filter(Objects::nonNull).collect(Collectors.toSet());
        DiscussionView view = toViews(user, List.of(discussion)).get(0);
        return new DiscussionDetailView(view, views.tree(forum, visible, withReplies));
    }

    @Transactional
    public void setPinned(UUID discussionId, boolean pinned) {
        Discussion discussion = moderate(discussionId, pinned ? "forum.discussion_pinned" : "forum.discussion_unpinned");
        repository.setPinned(discussion.tenantId(), discussionId, pinned);
    }

    @Transactional
    public void setLocked(UUID discussionId, boolean locked) {
        Discussion discussion = moderate(discussionId, locked ? "forum.discussion_locked" : "forum.discussion_unlocked");
        repository.setLocked(discussion.tenantId(), discussionId, locked);
    }

    @Transactional
    public void setSubscribed(UUID discussionId, boolean subscribed) {
        CurrentUser user = currentUser.require();
        Discussion discussion = readable(user, discussionId);
        if (subscribed) {
            repository.subscribe(user.tenantId(), discussion.id(), user.userId(), clock.instant());
        } else {
            repository.unsubscribe(user.tenantId(), discussion.id(), user.userId());
        }
    }

    @Transactional
    public void markRead(UUID discussionId) {
        CurrentUser user = currentUser.require();
        Discussion discussion = readable(user, discussionId);
        repository.markRead(user.tenantId(), discussion.id(), user.userId(), clock.instant());
    }

    private void notifyCreated(ForumContext forum, Discussion discussion, ForumPost root, Set<UUID> mentioned, String authorName) {
        if (forum.settings().forumType() == ForumType.ANNOUNCEMENTS) {
            notifier.announcement(forum, discussion, authorName);
        }
        notifier.mentions(forum, discussion, root, mentioned, authorName);
    }

    private Discussion moderate(UUID discussionId, String action) {
        CurrentUser user = currentUser.require();
        Discussion discussion = requireDiscussion(user.tenantId(), discussionId);
        ForumContext forum = forums.requireReadable(user.tenantId(), user.userId(), discussion.itemId());
        forum.require(Permission.FORUM_MODERATE);
        audit.record(AuditRecord.of(user.tenantId(), user.userId(), action, "discussion", discussionId.toString()));
        return discussion;
    }

    private Discussion readable(CurrentUser user, UUID discussionId) {
        Discussion discussion = requireDiscussion(user.tenantId(), discussionId);
        forums.requireReadable(user.tenantId(), user.userId(), discussion.itemId());
        return discussion;
    }

    private Discussion requireDiscussion(UUID tenantId, UUID discussionId) {
        return repository.findDiscussion(tenantId, discussionId).orElseThrow(ForumPostEffects::discussionNotFound);
    }

    private List<DiscussionView> toViews(CurrentUser user, List<Discussion> discussions) {
        Map<UUID, String> names = views.names(user.tenantId(), discussions.stream().map(Discussion::authorId).toList());
        Map<UUID, ReaderState> states = repository.readerStates(user.tenantId(), user.userId(),
                discussions.stream().map(Discussion::id).toList());
        return discussions.stream().map(d -> views.discussion(d, names.get(d.authorId()),
                states.getOrDefault(d.id(), ReaderState.NONE))).toList();
    }
}
