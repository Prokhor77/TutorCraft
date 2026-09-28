package com.tutorcraft.core.communication.forum.application;

import com.tutorcraft.core.communication.forum.application.ForumItems.ForumContext;
import com.tutorcraft.core.communication.forum.application.ForumRepository.ReaderState;
import com.tutorcraft.core.communication.forum.application.ForumViews.DiscussionView;
import com.tutorcraft.core.communication.forum.application.ForumViews.PostView;
import com.tutorcraft.core.communication.forum.domain.Discussion;
import com.tutorcraft.core.communication.forum.domain.ForumPost;
import com.tutorcraft.core.communication.forum.domain.PostPolicy;
import com.tutorcraft.core.communication.forum.domain.PostTree;
import com.tutorcraft.core.identity.UsersApi;
import com.tutorcraft.core.identity.UsersApi.UserRef;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Сборка представлений тем и постов: имена авторов, права правки/удаления для текущего пользователя. */
@Component
public class ForumViewAssembler {

    private final UsersApi users;
    private final Clock clock;

    public ForumViewAssembler(UsersApi users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    public Map<UUID, String> names(UUID tenantId, Collection<UUID> userIds) {
        Map<UUID, UserRef> found = users.findAll(tenantId, Set.copyOf(userIds));
        return userIds.stream().distinct().collect(Collectors.toMap(id -> id,
                id -> found.containsKey(id) ? found.get(id).displayName() : ""));
    }

    public DiscussionView discussion(Discussion discussion, String authorName, ReaderState state) {
        return new DiscussionView(discussion.id(), discussion.title(), discussion.authorId(), authorName, discussion.pinned(),
                discussion.locked(), state.subscribed(), discussion.replyCount(), state.unreadCount(), discussion.lastPostAt(),
                discussion.createdAt());
    }

    /**
     * @param visible   посты, видимые пользователю
     * @param withReplies посты, у которых есть ответы (среди всех неудалённых)
     */
    public List<PostView> tree(ForumContext context, List<ForumPost> visible, Set<UUID> withReplies) {
        Map<UUID, String> names = names(context.tenantId(), visible.stream().map(ForumPost::authorId).toList());
        Instant now = clock.instant();
        return PostTree.build(visible, (post, children) -> post(context, post, names.get(post.authorId()),
                withReplies.contains(post.id()), now, children));
    }

    public PostView post(ForumContext context, ForumPost post, String authorName, boolean hasReplies, Instant now,
                         List<PostView> children) {
        boolean moderator = context.moderator();
        return new PostView(post.id(), post.parentId(), post.authorId(), authorName, post.body(), post.createdAt(),
                post.editedAt(), PostPolicy.canEdit(post, context.userId(), moderator, now, context.settings().editWindow()),
                PostPolicy.canDelete(post, context.userId(), moderator, now, context.settings().editWindow(), hasReplies),
                post.hidden(), children);
    }
}
