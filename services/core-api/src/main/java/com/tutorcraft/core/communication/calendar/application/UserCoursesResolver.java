package com.tutorcraft.core.communication.calendar.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.CourseRole;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.courses.CoursesApi;
import com.tutorcraft.core.enrollment.EnrollmentApi;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Курсы с активной записью пользователя, разделённые по роли, и курсы, где он может назначать занятия. Удалённые курсы
 * отбрасываются: запись на них остаётся активной, а проверка прав по такому курсу отвечает 404.
 */
@Component
class UserCoursesResolver {

    private static final Set<CourseRole> STAFF = Set.of(CourseRole.TEACHER, CourseRole.ASSISTANT);
    private static final Set<CourseRole> LEARNERS = Set.of(CourseRole.STUDENT, CourseRole.OBSERVER, CourseRole.GUEST);

    private final EnrollmentApi enrollment;
    private final AccessService access;
    private final CoursesApi courses;

    UserCoursesResolver(EnrollmentApi enrollment, AccessService access, CoursesApi courses) {
        this.enrollment = enrollment;
        this.access = access;
        this.courses = courses;
    }

    UserCourses resolve(UUID tenantId, UUID userId) {
        Set<UUID> staff = new HashSet<>(enrollment.activeCourseIds(tenantId, userId, STAFF));
        Set<UUID> learner = new HashSet<>(enrollment.activeCourseIds(tenantId, userId, LEARNERS));
        learner.removeAll(staff);
        Set<UUID> existing = existing(tenantId, staff, learner);
        staff.retainAll(existing);
        learner.retainAll(existing);
        Set<UUID> editable = staff.stream()
                .filter(courseId -> access.permissionsOf(tenantId, userId, AccessContext.course(courseId))
                        .contains(Permission.COURSE_EDIT))
                .collect(Collectors.toSet());
        return new UserCourses(staff, learner, editable);
    }

    private Set<UUID> existing(UUID tenantId, Set<UUID> staff, Set<UUID> learner) {
        Set<UUID> all = new HashSet<>(staff);
        all.addAll(learner);
        return all.isEmpty() ? Set.of() : courses.findCourses(tenantId, all).keySet();
    }
}
