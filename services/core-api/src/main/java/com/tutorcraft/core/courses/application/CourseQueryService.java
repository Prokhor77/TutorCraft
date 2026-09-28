package com.tutorcraft.core.courses.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.CourseRole;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.courses.application.CourseRepository.CourseFilter;
import com.tutorcraft.core.courses.application.CourseRepository.CourseScope;
import com.tutorcraft.core.courses.domain.Course;
import com.tutorcraft.core.enrollment.EnrollmentApi;
import com.tutorcraft.core.progress.ProgressApi;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.api.PageResponse;
import com.tutorcraft.core.shared.domain.ForbiddenException;
import com.tutorcraft.core.shared.domain.NotFoundException;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Чтение курсов: карточка курса и списки (FR-COURSE-02, FR-COURSE-11). */
@Service
public class CourseQueryService {

    static final String ACTIVE = "active";
    private static final int MAX_QUERY_LENGTH = 200;

    private final CourseRepository courses;
    private final AccessService access;
    private final CurrentUserProvider currentUser;
    private final EnrollmentApi enrollment;
    private final ObjectProvider<ProgressApi> progress;
    private final CourseScopes scopes;
    private final CourseViews views;
    private final Clock clock;

    public CourseQueryService(CourseRepository courses, AccessService access, CurrentUserProvider currentUser,
                              EnrollmentApi enrollment, ObjectProvider<ProgressApi> progress, CourseScopes scopes,
                              CourseViews views, Clock clock) {
        this.courses = courses;
        this.access = access;
        this.currentUser = currentUser;
        this.enrollment = enrollment;
        this.progress = progress;
        this.scopes = scopes;
        this.views = views;
        this.clock = clock;
    }

    /** Курс чужого tenant → 404 + аудит (AC-1, через AccessService); скрытый курс учащемуся → 403 course.hidden. */
    @Transactional(noRollbackFor = NotFoundException.class)
    public CourseView get(UUID courseId) {
        CurrentUser user = currentUser.require();
        Set<Permission> permissions = access.permissions(AccessContext.course(courseId));
        PermissionChecks.require(permissions, Permission.COURSE_VIEW);
        Course course = courses.find(user.tenantId(), courseId).orElseThrow(CoursesErrors::courseNotFound);
        if (!permissions.contains(Permission.COURSE_VIEW_HIDDEN) && !course.visibleToLearnersAt(clock.instant())) {
            throw new ForbiddenException(CoursesErrors.COURSE_HIDDEN, "Course is not available yet");
        }
        CourseRole role = enrollment.membership(user.tenantId(), courseId, user.userId())
                .filter(member -> ACTIVE.equals(member.status()))
                .map(EnrollmentApi.Member::role)
                .orElse(null);
        return views.course(course, permissions, role);
    }

    @Transactional(readOnly = true)
    public PageResponse<CourseCardView> list(String q, UUID categoryId, boolean mine, PageQuery page) {
        CurrentUser user = currentUser.require();
        CourseScope scope = scopes.viewable(user, mine);
        CourseFilter filter = new CourseFilter(normalizeQuery(q), categoryId, scope);
        List<Course> rows = courses.list(user.tenantId(), filter, page, clock.instant());
        PageResponse<Course> pageOfCourses = page.toPage(rows, Course::createdAt, Course::id);
        Map<UUID, CourseRole> roles = scopes.activeRoles(user);
        Map<UUID, Integer> percents = progressOf(user, pageOfCourses.items());
        return pageOfCourses.map(course -> views.card(course, roles.get(course.id()), percents.get(course.id())));
    }

    private Map<UUID, Integer> progressOf(CurrentUser user, List<Course> page) {
        ProgressApi api = progress.getIfAvailable();
        if (api == null || page.isEmpty()) {
            return Map.of();
        }
        return api.completionPercents(user.tenantId(), user.userId(), page.stream().map(Course::id).toList());
    }

    private static String normalizeQuery(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String trimmed = q.trim();
        return trimmed.length() > MAX_QUERY_LENGTH ? trimmed.substring(0, MAX_QUERY_LENGTH) : trimmed;
    }
}
