package com.tutorcraft.core.courses.domain;

import java.time.Instant;

/**
 * Видимость для студента без учёта условий доступа (FR-COURSE-04): элемент виден, только если видимы курс,
 * модуль, родительский модуль (если есть) и сам элемент, и ничто из них не удалено.
 */
public final class LearnerVisibility {

    private LearnerVisibility() {
    }

    public static boolean courseVisible(Course course, Instant now) {
        return course != null && course.visibleToLearnersAt(now);
    }

    /** parent — родительский модуль, если module вложенный (иначе null). */
    public static boolean moduleVisible(CourseModule module, CourseModule parent, Instant now) {
        if (module == null || module.isDeleted() || !module.visibleAt(now)) {
            return false;
        }
        if (module.isTopLevel()) {
            return true;
        }
        return parent != null && parent.id().equals(module.parentId()) && !parent.isDeleted() && parent.visibleAt(now);
    }

    public static boolean itemVisible(Course course, CourseModule module, CourseModule parent, CourseItem item, Instant now) {
        return courseVisible(course, now)
                && moduleVisible(module, parent, now)
                && item != null && !item.isDeleted() && item.moduleId().equals(module.id())
                && item.visibleAt(now);
    }
}
