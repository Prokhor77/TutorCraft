package com.tutorcraft.core.courses.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.courses.domain.Course;
import com.tutorcraft.core.courses.domain.CourseItem;
import com.tutorcraft.core.courses.domain.CourseModule;
import com.tutorcraft.core.courses.domain.LearnerVisibility;
import com.tutorcraft.core.files.spi.FileOwnerAccess;
import java.time.Clock;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Файлы элемента курса (NFR-SEC-02): персонал (course.viewHidden) — всегда; иначе content.view в курсе
 * и элемент видим студентам (курс, модули и элемент опубликованы).
 */
@Component
class ItemFileOwnerAccess implements FileOwnerAccess {

    private final CourseRepository courses;
    private final ModuleRepository modules;
    private final ItemRepository items;
    private final AccessService access;
    private final Clock clock;

    ItemFileOwnerAccess(CourseRepository courses, ModuleRepository modules, ItemRepository items, AccessService access,
                        Clock clock) {
        this.courses = courses;
        this.modules = modules;
        this.items = items;
        this.access = access;
        this.clock = clock;
    }

    @Override
    public String ownerType() {
        return CourseContentFiles.OWNER_ITEM;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canRead(UUID tenantId, UUID userId, UUID ownerId) {
        Optional<CourseItem> item = items.find(tenantId, ownerId);
        Optional<Course> course = item.flatMap(found -> courses.find(tenantId, found.courseId()));
        if (item.isEmpty() || course.isEmpty()) {
            return false;
        }
        Set<Permission> permissions = access.permissionsOf(tenantId, userId, AccessContext.course(course.get().id()));
        if (permissions.contains(Permission.COURSE_VIEW_HIDDEN)) {
            return true;
        }
        return permissions.contains(Permission.CONTENT_VIEW) && visibleToLearners(course.get(), item.get());
    }

    private boolean visibleToLearners(Course course, CourseItem item) {
        Optional<CourseModule> module = modules.find(course.tenantId(), item.moduleId());
        if (module.isEmpty()) {
            return false;
        }
        CourseModule parent = module.get().isTopLevel() ? null
                : modules.find(course.tenantId(), module.get().parentId()).orElse(null);
        return LearnerVisibility.itemVisible(course, module.get(), parent, item, clock.instant());
    }
}
