package com.tutorcraft.core.courses.application;

import com.tutorcraft.core.access.domain.CourseRole;
import com.tutorcraft.core.courses.application.PublicCourseView.Catalog;
import com.tutorcraft.core.courses.application.PublicCourseView.ModuleSummary;
import com.tutorcraft.core.courses.application.PublicCourseView.Teacher;
import com.tutorcraft.core.courses.domain.Course;
import com.tutorcraft.core.courses.domain.CourseItem;
import com.tutorcraft.core.courses.domain.CourseModule;
import com.tutorcraft.core.courses.domain.LearnerVisibility;
import com.tutorcraft.core.enrollment.EnrollmentApi;
import com.tutorcraft.core.identity.UsersApi;
import com.tutorcraft.core.identity.UsersApi.UserRef;
import com.tutorcraft.core.org.OrgApi;
import com.tutorcraft.core.org.OrgApi.TenantInfo;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Публичная витрина и лендинг курса без авторизации (FR-COURSE-HYB-01, SSR/SEO). Только опубликованные курсы;
 * ответы кэшируются на короткое время ({@link PublicCatalogCache}) и сбрасываются при изменении курсов tenant.
 */
@Service
public class PublicCatalogService {

    private static final String CATALOG_KEY = "catalog";
    private static final String COURSE_KEY_PREFIX = "course:";

    private final OrgApi org;
    private final CourseRepository courses;
    private final ModuleRepository modules;
    private final ItemRepository items;
    private final EnrollmentApi enrollment;
    private final UsersApi users;
    private final CourseViews views;
    private final CourseContentFiles files;
    private final PublicCatalogCache cache;
    private final Clock clock;

    public PublicCatalogService(OrgApi org, CourseRepository courses, ModuleRepository modules, ItemRepository items,
                                EnrollmentApi enrollment, UsersApi users, CourseViews views, CourseContentFiles files,
                                PublicCatalogCache cache, Clock clock) {
        this.org = org;
        this.courses = courses;
        this.modules = modules;
        this.items = items;
        this.enrollment = enrollment;
        this.users = users;
        this.views = views;
        this.files = files;
        this.cache = cache;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PublicCourseView> catalog(String tenantSlug) {
        TenantInfo tenant = requireTenant(tenantSlug);
        Optional<Catalog> cached = cache.get(tenant.id(), CATALOG_KEY, Catalog.class);
        if (cached.isPresent()) {
            return cached.get().courses();
        }
        Instant now = clock.instant();
        List<PublicCourseView> result = courses.published(tenant.id(), now).stream()
                .map(course -> toView(tenant, course, now))
                .toList();
        cache.put(tenant.id(), CATALOG_KEY, new Catalog(result));
        return result;
    }

    @Transactional(readOnly = true)
    public PublicCourseView course(String tenantSlug, String courseSlug) {
        TenantInfo tenant = requireTenant(tenantSlug);
        String key = COURSE_KEY_PREFIX + courseSlug;
        Optional<PublicCourseView> cached = cache.get(tenant.id(), key, PublicCourseView.class);
        if (cached.isPresent()) {
            return cached.get();
        }
        Instant now = clock.instant();
        Course course = courses.findBySlug(tenant.id(), courseSlug)
                .filter(found -> found.visibleToLearnersAt(now))
                .orElseThrow(CoursesErrors::courseNotFound);
        PublicCourseView view = toView(tenant, course, now);
        cache.put(tenant.id(), key, view);
        return view;
    }

    private TenantInfo requireTenant(String tenantSlug) {
        return org.findBySlug(tenantSlug).filter(TenantInfo::active).orElseThrow(CoursesErrors::courseNotFound);
    }

    private PublicCourseView toView(TenantInfo tenant, Course course, Instant now) {
        return new PublicCourseView(course.id(), course.slug(), course.title(), course.description(), views.coverUrl(course),
                teacher(tenant.id(), course.id()), moduleSummaries(course, now), course.selfEnrol().enabled(),
                tenant.slug(), tenant.name());
    }

    /** Первый (по порядку записи) активный преподаватель; без преподавателя — пустое имя. */
    private Teacher teacher(UUID tenantId, UUID courseId) {
        return enrollment.activeMembers(tenantId, courseId, EnumSet.of(CourseRole.TEACHER)).stream()
                .findFirst()
                .flatMap(member -> users.find(tenantId, member.userId()))
                .map(user -> new Teacher(user.displayName(), avatarUrl(tenantId, user)))
                .orElse(new Teacher("", null));
    }

    private String avatarUrl(UUID tenantId, UserRef user) {
        return files.downloadUrl(tenantId, user.avatarFileId()).orElse(null);
    }

    /** Видимые студентам модули верхнего уровня с числом видимых элементов (включая подмодули). */
    private List<ModuleSummary> moduleSummaries(Course course, Instant now) {
        List<CourseModule> all = modules.ofCourse(course.tenantId(), course.id());
        Map<UUID, CourseModule> byId = all.stream().collect(Collectors.toMap(CourseModule::id, Function.identity()));
        Map<UUID, Long> visibleItemsByModule = items.ofCourse(course.tenantId(), course.id()).stream()
                .filter(item -> isVisible(course, byId, item, now))
                .collect(Collectors.groupingBy(item -> rootOf(byId.get(item.moduleId())), Collectors.counting()));
        return all.stream()
                .filter(module -> module.isTopLevel() && LearnerVisibility.moduleVisible(module, null, now))
                .sorted(Comparator.comparingInt(CourseModule::position))
                .map(module -> new ModuleSummary(module.title(), visibleItemsByModule.getOrDefault(module.id(), 0L).intValue()))
                .toList();
    }

    private static boolean isVisible(Course course, Map<UUID, CourseModule> modulesById, CourseItem item, Instant now) {
        CourseModule module = modulesById.get(item.moduleId());
        CourseModule parent = module == null || module.isTopLevel() ? null : modulesById.get(module.parentId());
        return module != null && LearnerVisibility.itemVisible(course, module, parent, item, now);
    }

    private static UUID rootOf(CourseModule module) {
        return module.isTopLevel() ? module.id() : module.parentId();
    }
}
