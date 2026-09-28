package com.tutorcraft.core.communication.forum.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.communication.forum.domain.ForumSettings;
import com.tutorcraft.core.courses.CoursesApi;
import com.tutorcraft.core.courses.ItemRef;
import com.tutorcraft.core.courses.ItemType;
import com.tutorcraft.core.progress.LearnerAccess;
import com.tutorcraft.core.shared.domain.ForbiddenException;
import com.tutorcraft.core.shared.domain.NotFoundException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Загрузка элемента-форума, права пользователя в курсе и проверка доступа студента (видимость + условия). */
@Component
public class ForumItems {

    private static final String ACCESS_DENIED = "access.denied";

    private final CoursesApi courses;
    private final AccessService access;
    private final LearnerAccess learnerAccess;

    public ForumItems(CoursesApi courses, AccessService access, LearnerAccess learnerAccess) {
        this.courses = courses;
        this.access = access;
        this.learnerAccess = learnerAccess;
    }

    /**
     * Форум, открытый текущему пользователю для чтения ({@code content.view} + доступность для студента).
     * @throws NotFoundException элемента нет, это не форум или он скрыт
     */
    public ForumContext requireReadable(UUID tenantId, UUID userId, UUID itemId) {
        ItemRef item = courses.requireItem(tenantId, itemId);
        if (item.type() != ItemType.FORUM) {
            throw new NotFoundException(ForumErrors.NOT_FOUND, "Forum not found");
        }
        Set<Permission> permissions = access.permissions(AccessContext.course(item.courseId()));
        if (!permissions.contains(Permission.CONTENT_VIEW)) {
            throw denied(Permission.CONTENT_VIEW);
        }
        if (!permissions.contains(Permission.COURSE_VIEW_HIDDEN)) {
            requireOpen(tenantId, userId, item);
        }
        return new ForumContext(item, ForumSettings.parse(item.settings()), permissions, userId);
    }

    private void requireOpen(UUID tenantId, UUID userId, ItemRef item) {
        LearnerAccess.Status status = learnerAccess.statusOf(tenantId, userId, item);
        if (status == LearnerAccess.Status.HIDDEN) {
            throw new NotFoundException(ForumErrors.ITEM_NOT_FOUND, "Item not found");
        }
        if (status == LearnerAccess.Status.LOCKED) {
            throw new ForbiddenException(ForumErrors.UNAVAILABLE, "Forum is not available yet");
        }
    }

    static ForbiddenException denied(Permission permission) {
        return new ForbiddenException(ACCESS_DENIED, "Missing permission " + permission.key(),
                Map.of("permission", permission.key()));
    }

    /** Форум и права текущего пользователя в его курсе. */
    public record ForumContext(ItemRef item, ForumSettings settings, Set<Permission> permissions, UUID userId) {

        public UUID tenantId() {
            return item.tenantId();
        }

        public UUID courseId() {
            return item.courseId();
        }

        public boolean can(Permission permission) {
            return permissions.contains(permission);
        }

        public boolean moderator() {
            return can(Permission.FORUM_MODERATE);
        }

        public void require(Permission permission) {
            if (!can(permission)) {
                throw denied(permission);
            }
        }
    }
}
