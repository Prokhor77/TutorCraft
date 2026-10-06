package com.tutorcraft.core.courses.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.CourseRole;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.courses.domain.Course;
import com.tutorcraft.core.enrollment.EnrollmentApi;
import com.tutorcraft.core.identity.UsersApi;
import com.tutorcraft.core.identity.UsersApi.UserRef;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.api.PageResponse;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Курсы всех школ для главного администратора платформы (право platform.manage): кто из репетиторов создал курс,
 * в какой школе, сколько в нём учеников и преподавателей. Материалы и участники конкретного курса админка читает
 * обычными эндпоинтами курса в выбранной школе (заголовок X-Tenant-Id).
 */
@Service
public class PlatformCoursesService {

    private static final int MAX_QUERY_LENGTH = 200;

    private final CourseRepository courses;
    private final CourseViews views;
    private final EnrollmentApi enrollment;
    private final UsersApi users;
    private final AccessService access;

    PlatformCoursesService(CourseRepository courses, CourseViews views, EnrollmentApi enrollment, UsersApi users,
                           AccessService access) {
        this.courses = courses;
        this.views = views;
        this.enrollment = enrollment;
        this.users = users;
        this.access = access;
    }

    /** Не удалённые курсы всех школ или одной ({@code tenantId}), новые первыми. */
    @Transactional(readOnly = true)
    public PageResponse<PlatformCourseView> list(UUID tenantId, String q, PageQuery page) {
        access.require(Permission.PLATFORM_MANAGE, AccessContext.tenant());
        List<Course> rows = courses.listAcrossTenants(tenantId, normalizeQuery(q), page);
        PageResponse<Course> pageOfCourses = page.toPage(rows, Course::createdAt, Course::id);
        Map<UUID, List<Course>> byTenant = pageOfCourses.items().stream()
                .collect(Collectors.groupingBy(Course::tenantId));
        Map<UUID, Map<CourseRole, Integer>> counts = new HashMap<>();
        Map<UUID, UserRef> authors = new HashMap<>();
        byTenant.forEach((tenant, tenantCourses) -> {
            counts.putAll(enrollment.activeCountsByRole(tenant, tenantCourses.stream().map(Course::id).toList()));
            authors.putAll(users.findAll(tenant, tenantCourses.stream().map(Course::createdBy)
                    .filter(Objects::nonNull).distinct().toList()));
        });
        return pageOfCourses.map(course -> toView(course, authors.get(course.createdBy()),
                counts.getOrDefault(course.id(), Map.of())));
    }

    private PlatformCourseView toView(Course course, UserRef author, Map<CourseRole, Integer> counts) {
        return new PlatformCourseView(course.id(), course.tenantId(), course.title(), course.shortName(),
                course.slug(), views.coverUrl(course), course.visibility().key(), course.publishAt(),
                course.startsAt(), course.endsAt(), course.createdAt(), course.updatedAt(),
                author == null ? null : new Person(author.id(), author.email(), author.firstName(), author.lastName()),
                counts.getOrDefault(CourseRole.STUDENT, 0),
                counts.getOrDefault(CourseRole.TEACHER, 0) + counts.getOrDefault(CourseRole.ASSISTANT, 0));
    }

    private static String normalizeQuery(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String trimmed = q.trim();
        return trimmed.length() > MAX_QUERY_LENGTH ? trimmed.substring(0, MAX_QUERY_LENGTH) : trimmed;
    }

    /**
     * @param author        репетитор, создавший курс; null — учётная запись удалена
     * @param studentsCount активные ученики
     * @param staffCount    активные преподаватели и ассистенты
     */
    public record PlatformCourseView(UUID id, UUID tenantId, String title, String shortName, String slug,
                                     String coverUrl, String visibility, Instant publishAt, Instant startsAt,
                                     Instant endsAt, Instant createdAt, Instant updatedAt, Person author,
                                     int studentsCount, int staffCount) {
    }

    public record Person(UUID id, String email, String firstName, String lastName) {
    }
}
