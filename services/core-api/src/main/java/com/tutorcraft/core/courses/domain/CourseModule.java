package com.tutorcraft.core.courses.domain;

import com.tutorcraft.core.courses.ModuleRef;
import com.tutorcraft.core.courses.Visibility;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Модуль (секция) курса — MongoDB, коллекция modules. Глубина ≤ 1: parentId указывает только на модуль верхнего уровня. */
public record CourseModule(UUID id, UUID tenantId, UUID courseId, UUID parentId, String title, int position,
                           Visibility visibility, Instant publishAt, Map<String, Object> conditions, long version,
                           Instant deletedAt) {

    public boolean isTopLevel() {
        return parentId == null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean visibleAt(Instant now) {
        return visibility.visibleAt(publishAt, now);
    }

    public CourseModule withPlacement(UUID newParentId, int newPosition) {
        return new CourseModule(id, tenantId, courseId, newParentId, title, newPosition, visibility, publishAt, conditions,
                version, deletedAt);
    }

    public CourseModule withTitle(String newTitle) {
        return new CourseModule(id, tenantId, courseId, parentId, newTitle, position, visibility, publishAt, conditions,
                version, deletedAt);
    }

    public CourseModule withContent(String newTitle, Visibility newVisibility, Instant newPublishAt,
                                    Map<String, Object> newConditions) {
        Instant effectivePublishAt = newVisibility == Visibility.SCHEDULED ? newPublishAt : null;
        return new CourseModule(id, tenantId, courseId, parentId, newTitle, position, newVisibility, effectivePublishAt,
                newConditions, version, deletedAt);
    }

    /** Копия для дублирования: новый id/курс/родитель, версия 0, не удалена. */
    public CourseModule copy(UUID newId, UUID newCourseId, UUID newParentId, String newTitle, int newPosition,
                             Visibility newVisibility, Map<String, Object> newConditions) {
        return new CourseModule(newId, tenantId, newCourseId, newParentId, newTitle, newPosition, newVisibility,
                newVisibility == Visibility.SCHEDULED ? publishAt : null, newConditions, 0, null);
    }

    public ModuleRef toRef() {
        return new ModuleRef(id, courseId, parentId, title, position, visibility, publishAt, conditions);
    }
}
