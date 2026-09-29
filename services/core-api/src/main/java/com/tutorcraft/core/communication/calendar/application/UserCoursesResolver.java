package com.tutorcraft.core.communication.calendar.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.CourseRole;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.enrollment.EnrollmentApi;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Курсы с активной записью пользователя, разделённые по роли, и курсы, где он может назначать занятия. */
@Component
class UserCoursesResolver {

    private static final Set<CourseRole> STAFF = Set.of(CourseRole.TEACHER, CourseRole.ASSISTANT);
    private static final Set<CourseRole> LEARNERS = Set.of(CourseRole.STUDENT, CourseRole.OBSERVER, CourseRole.GUEST);

    private final EnrollmentApi enrollment;
    private final AccessService access;

    UserCoursesResolver(EnrollmentApi enrollment, AccessService access) {
        this.enrollment = enrollment;
        this.access = access;
    }

    UserCourses resolve(UUID tenantId, UUID userId) {
        Set<UUID> staff = new HashSet<>(enrollment.activeCourseIds(tenantId, userId, STAFF));
        Set<UUID> learner = new HashSet<>(enrollment.activeCourseIds(tenantId, userId, LEARNERS));
        learner.removeAll(staff);
        Set<UUID> editable = staff.stream()
                .filter(courseId -> access.permissionsOf(tenantId, userId, AccessContext.course(courseId))
                        .contains(Permission.COURSE_EDIT))
                .collect(Collectors.toSet());
        return new UserCourses(staff, learner, editable);
    }
}
