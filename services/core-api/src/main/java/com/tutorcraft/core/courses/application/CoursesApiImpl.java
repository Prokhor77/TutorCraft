package com.tutorcraft.core.courses.application;

import com.tutorcraft.core.courses.CourseRef;
import com.tutorcraft.core.courses.CoursesApi;
import com.tutorcraft.core.courses.ItemRef;
import com.tutorcraft.core.courses.ItemType;
import com.tutorcraft.core.courses.ModuleRef;
import com.tutorcraft.core.courses.domain.Course;
import com.tutorcraft.core.courses.domain.CourseItem;
import com.tutorcraft.core.courses.domain.CourseModule;
import com.tutorcraft.core.courses.domain.LearnerVisibility;
import com.tutorcraft.core.courses.domain.SelfEnrolSettings;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Реализация CoursesApi: только репозитории и часы (правило против циклов бинов).
 * Элементы и модули курсов, находящихся в корзине, не возвращаются.
 */
@Component
class CoursesApiImpl implements CoursesApi {

    private final CourseRepository courses;
    private final ModuleRepository modules;
    private final ItemRepository items;
    private final Clock clock;

    CoursesApiImpl(CourseRepository courses, ModuleRepository modules, ItemRepository items, Clock clock) {
        this.courses = courses;
        this.modules = modules;
        this.items = items;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public CourseRef requireCourse(UUID tenantId, UUID courseId) {
        return findCourse(tenantId, courseId).orElseThrow(CoursesErrors::courseNotFound);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CourseRef> findCourse(UUID tenantId, UUID courseId) {
        return courses.find(tenantId, courseId).map(Course::toRef);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CourseRef> findByShortName(UUID tenantId, String shortName) {
        if (shortName == null || shortName.isBlank()) {
            return Optional.empty();
        }
        return courses.findByShortName(tenantId, shortName.trim()).map(Course::toRef);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, CourseRef> findCourses(UUID tenantId, Collection<UUID> courseIds) {
        if (courseIds.isEmpty()) {
            return Map.of();
        }
        return courses.findAll(tenantId, courseIds).values().stream()
                .collect(Collectors.toMap(Course::id, Course::toRef));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Integer> countByCategory(UUID tenantId) {
        return courses.countByCategory(tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public ItemRef requireItem(UUID tenantId, UUID itemId) {
        return items.find(tenantId, itemId)
                .filter(item -> courses.find(tenantId, item.courseId()).isPresent())
                .map(CourseItem::toRef)
                .orElseThrow(CoursesErrors::itemNotFound);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, ItemRef> findItems(UUID tenantId, Collection<UUID> itemIds) {
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        List<CourseItem> found = List.copyOf(items.findAll(tenantId, itemIds).values());
        Set<UUID> liveCourses = liveCourseIds(tenantId, found.stream().map(CourseItem::courseId).collect(Collectors.toSet()));
        return found.stream()
                .filter(item -> liveCourses.contains(item.courseId()))
                .collect(Collectors.toMap(CourseItem::id, CourseItem::toRef));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItemRef> itemsOfCourse(UUID tenantId, UUID courseId) {
        if (courses.find(tenantId, courseId).isEmpty()) {
            return List.of();
        }
        return items.ofCourse(tenantId, courseId).stream().map(CourseItem::toRef).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ModuleRef> modulesOfCourse(UUID tenantId, UUID courseId) {
        if (courses.find(tenantId, courseId).isEmpty()) {
            return List.of();
        }
        return modules.ofCourse(tenantId, courseId).stream().map(CourseModule::toRef).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItemRef> itemsOfCourses(UUID tenantId, Collection<UUID> courseIds, Set<ItemType> types) {
        if (courseIds.isEmpty()) {
            return List.of();
        }
        Set<UUID> live = liveCourseIds(tenantId, courseIds);
        if (live.isEmpty()) {
            return List.of();
        }
        return items.ofCourses(tenantId, live, types == null ? Set.of() : types).stream()
                .sorted(Comparator.comparing(CourseItem::courseId).thenComparingInt(CourseItem::position))
                .map(CourseItem::toRef)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItemRef> itemsDueBetween(Instant fromExclusive, Instant toInclusive) {
        Map<UUID, List<CourseItem>> byTenant = items.dueBetween(fromExclusive, toInclusive).stream()
                .collect(Collectors.groupingBy(CourseItem::tenantId));
        return byTenant.entrySet().stream()
                .flatMap(entry -> inLiveCourses(entry.getKey(), entry.getValue()).stream())
                .map(CourseItem::toRef)
                .toList();
    }

    private List<CourseItem> inLiveCourses(UUID tenantId, List<CourseItem> candidates) {
        Set<UUID> live = liveCourseIds(tenantId, candidates.stream().map(CourseItem::courseId).collect(Collectors.toSet()));
        return candidates.stream().filter(item -> live.contains(item.courseId())).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isVisibleToLearners(UUID tenantId, ItemRef item) {
        Optional<CourseItem> current = items.find(tenantId, item.id());
        if (current.isEmpty()) {
            return false;
        }
        Optional<Course> course = courses.find(tenantId, current.get().courseId());
        Optional<CourseModule> module = modules.find(tenantId, current.get().moduleId());
        if (course.isEmpty() || module.isEmpty()) {
            return false;
        }
        CourseModule parent = module.get().isTopLevel() ? null : modules.find(tenantId, module.get().parentId()).orElse(null);
        return LearnerVisibility.itemVisible(course.get(), module.get(), parent, current.get(), clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SelfEnrolment> selfEnrolment(UUID tenantId, UUID courseId) {
        return courses.find(tenantId, courseId).map(Course::selfEnrol).map(CoursesApiImpl::toSelfEnrolment);
    }

    private static SelfEnrolment toSelfEnrolment(SelfEnrolSettings settings) {
        return new SelfEnrolment(settings.enabled(), settings.code(), settings.maxStudents(), settings.until());
    }

    private Set<UUID> liveCourseIds(UUID tenantId, Collection<UUID> courseIds) {
        return Set.copyOf(courses.findAll(tenantId, courseIds).keySet());
    }
}
