package com.tutorcraft.core.communication.forum.application;

import com.tutorcraft.core.communication.ForumEvents.PostCreated;
import com.tutorcraft.core.communication.forum.application.ForumItems.ForumContext;
import com.tutorcraft.core.communication.forum.domain.Discussion;
import com.tutorcraft.core.communication.forum.domain.ForumPost;
import com.tutorcraft.core.shared.domain.NotFoundException;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Общие последствия нового поста: автор подписан на тему и прочитал её; событие для выполнения (FR-PROG-01). */
@Component
public class ForumPostEffects {

    private final ForumRepository repository;
    private final ApplicationEventPublisher events;

    public ForumPostEffects(ForumRepository repository, ApplicationEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public void afterPost(ForumContext forum, Discussion discussion, ForumPost post, Instant now) {
        repository.subscribe(post.tenantId(), discussion.id(), post.authorId(), now);
        repository.markRead(post.tenantId(), discussion.id(), post.authorId(), now);
        events.publishEvent(new PostCreated(post.tenantId(), forum.courseId(), forum.item().id(), discussion.id(), post.id(),
                post.authorId()));
    }

    public static NotFoundException discussionNotFound() {
        return new NotFoundException(ForumErrors.DISCUSSION_NOT_FOUND, "Discussion not found");
    }
}
