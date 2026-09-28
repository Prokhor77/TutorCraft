package com.tutorcraft.core.dashboard.application;

import com.tutorcraft.core.access.domain.CourseRole;
import com.tutorcraft.core.courses.CourseRef;
import com.tutorcraft.core.courses.CoursesApi;
import com.tutorcraft.core.dashboard.application.DashboardViews.CourseCard;
import com.tutorcraft.core.enrollment.EnrollmentApi;
import com.tutorcraft.core.progress.ProgressApi;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * «Мои курсы» (контракт §2 CourseCard): курсы с активной записью; роль — старшая из записей; студенту — только
 * опубликованные курсы и прогресс.
 */
@Service
public class MyCoursesService {

    /** Порядок старшинства ролей в карточке курса. */
    private static final List<CourseRole> ROLE_PRIORITY = List.of(CourseRole.TEACHER, CourseRole.ASSISTANT,
            CourseRole.STUDENT, CourseRole.OBSERVER, CourseRole.GUEST);
    private static final Set<CourseRole> STAFF = Set.of(CourseRole.TEACHER, CourseRole.ASSISTANT);

    private final CurrentUserProvider currentUser;
    private final EnrollmentApi enrollment;
    private final CoursesApi courses;
    private final ObjectProvider<ProgressApi> progress;
    private final Clock clock;

    MyCoursesService(CurrentUserProvider currentUser, EnrollmentApi enrollment, CoursesApi courses,
                     ObjectProvider<ProgressApi> progress, Clock clock) {
        this.currentUser = currentUser;
        this.enrollment = enrollment;
        this.courses = courses;
        this.progress = progress;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CourseCard> courses() {
        CurrentUser user = currentUser.require();
        Map<UUID, CourseRole> roles = roles(user);
        Instant now = clock.instant();
        List<CourseRef> visible = courses.findCourses(user.tenantId(), roles.keySet()).values().stream()
                .filter(course -> STAFF.contains(roles.get(course.id())) || course.visibility().visibleAt(course.publishAt(), now))
                .sorted(Comparator.comparing(CourseRef::title, String.CASE_INSENSITIVE_ORDER))
                .toList();
        Map<UUID, Integer> percents = progressOf(user, visible.stream()
                .filter(course -> roles.get(course.id()) == CourseRole.STUDENT).map(CourseRef::id).toList());
        return visible.stream().map(course -> card(course, roles.get(course.id()), percents.get(course.id()))).toList();
    }

    private Map<UUID, CourseRole> roles(CurrentUser user) {
        Map<UUID, CourseRole> roles = new LinkedHashMap<>();
        ROLE_PRIORITY.forEach(role -> enrollment.activeCourseIds(user.tenantId(), user.userId(), Set.of(role))
                .forEach(courseId -> roles.putIfAbsent(courseId, role)));
        return roles;
    }

    private Map<UUID, Integer> progressOf(CurrentUser user, List<UUID> studentCourses) {
        ProgressApi progressApi = progress.getIfAvailable();
        if (progressApi == null || studentCourses.isEmpty()) {
            return Map.of();
        }
        return progressApi.completionPercents(user.tenantId(), user.userId(), studentCourses);
    }

    /** coverUrl — null, пока CourseRef не содержит coverFileId (запрошено у courses). */
    private static CourseCard card(CourseRef course, CourseRole role, Integer progressPercent) {
        return new CourseCard(course.id(), course.title(), course.shortName(), null, course.categoryId(), role.key(),
                progressPercent, course.visibility().key(), course.price());
    }
}
