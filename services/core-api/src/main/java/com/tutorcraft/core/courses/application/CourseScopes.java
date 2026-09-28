package com.tutorcraft.core.courses.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.CourseRole;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.courses.application.CourseRepository.CourseScope;
import com.tutorcraft.core.enrollment.EnrollmentApi;
import com.tutorcraft.core.shared.security.CurrentUser;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Область курсов пользователя для списков и корзины: права уровня tenant → все курсы; иначе курсы категорий
 * под управлением + курсы с активной записью (для учащихся — только видимые студентам).
 */
@Component
class CourseScopes {

    static final Set<CourseRole> STAFF_ROLES = EnumSet.of(CourseRole.TEACHER, CourseRole.ASSISTANT);
    static final Set<CourseRole> LEARNER_ROLES = EnumSet.of(CourseRole.STUDENT, CourseRole.OBSERVER, CourseRole.GUEST);

    private final AccessService access;
    private final EnrollmentApi enrollment;
    private final CourseRepository courses;

    CourseScopes(AccessService access, EnrollmentApi enrollment, CourseRepository courses) {
        this.access = access;
        this.enrollment = enrollment;
        this.courses = courses;
    }

    /** Курсы для списка: mineOnly — только курсы с активной записью (контракт: ?mine=true). */
    CourseScope viewable(CurrentUser user, boolean mineOnly) {
        if (!mineOnly && access.can(Permission.COURSE_VIEW_HIDDEN, AccessContext.tenant())) {
            return CourseScope.everything();
        }
        List<UUID> staff = enrollment.activeCourseIds(user.tenantId(), user.userId(), STAFF_ROLES);
        List<UUID> learner = enrollment.activeCourseIds(user.tenantId(), user.userId(), LEARNER_ROLES);
        List<UUID> categories = mineOnly ? List.of() : managedCategories(user, Permission.COURSE_VIEW_HIDDEN);
        return new CourseScope(false, staff, learner, categories);
    }

    /** Курсы, которые пользователь может восстановить из корзины. */
    CourseScope restorable(CurrentUser user) {
        if (access.can(Permission.COURSE_DELETE, AccessContext.tenant())) {
            return CourseScope.everything();
        }
        List<UUID> teaching = enrollment.activeCourseIds(user.tenantId(), user.userId(), EnumSet.of(CourseRole.TEACHER));
        return new CourseScope(false, teaching, List.of(), managedCategories(user, Permission.COURSE_DELETE));
    }

    /** Роль пользователя в каждом курсе с активной записью (по одному запросу на роль, не на курс). */
    Map<UUID, CourseRole> activeRoles(CurrentUser user) {
        Map<UUID, CourseRole> result = new HashMap<>();
        for (CourseRole role : CourseRole.values()) {
            enrollment.activeCourseIds(user.tenantId(), user.userId(), EnumSet.of(role))
                    .forEach(courseId -> result.putIfAbsent(courseId, role));
        }
        return result;
    }

    private List<UUID> managedCategories(CurrentUser user, Permission permission) {
        return courses.usedCategoryIds(user.tenantId()).stream()
                .filter(categoryId -> access.can(permission, AccessContext.category(categoryId)))
                .toList();
    }
}
