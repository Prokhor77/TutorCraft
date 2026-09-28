package com.tutorcraft.core.communication.forum.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.communication.forum.domain.ForumPost;
import com.tutorcraft.core.files.spi.FileOwnerAccess;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Вложения постов (владелец 'post'): автор; участник курса с content.view, если пост не скрыт; модераторы — всегда. */
@Component
class PostFileOwnerAccess implements FileOwnerAccess {

    private final ForumRepository repository;
    private final AccessService access;

    PostFileOwnerAccess(ForumRepository repository, AccessService access) {
        this.repository = repository;
        this.access = access;
    }

    @Override
    public String ownerType() {
        return ForumContent.POST_OWNER;
    }

    @Override
    public boolean canRead(UUID tenantId, UUID userId, UUID ownerId) {
        ForumPost post = repository.findPost(tenantId, ownerId).orElse(null);
        if (post == null) {
            return false;
        }
        if (post.authorId().equals(userId)) {
            return true;
        }
        Set<Permission> permissions = access.permissionsOf(tenantId, userId, AccessContext.course(post.courseId()));
        return permissions.contains(Permission.FORUM_MODERATE)
                || (!post.hidden() && permissions.contains(Permission.CONTENT_VIEW));
    }
}
