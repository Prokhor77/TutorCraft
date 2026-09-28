package com.tutorcraft.core.communication.calendar.application;

import com.tutorcraft.core.access.domain.CourseRole;
import com.tutorcraft.core.communication.calendar.domain.CalendarEvent;
import com.tutorcraft.core.communication.calendar.domain.CourseCalendar;
import com.tutorcraft.core.communication.calendar.domain.CourseCalendar.ItemDates;
import com.tutorcraft.core.courses.CourseRef;
import com.tutorcraft.core.courses.CoursesApi;
import com.tutorcraft.core.courses.ItemRef;
import com.tutorcraft.core.courses.ItemType;
import com.tutorcraft.core.enrollment.EnrollmentApi;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * События курсов пользователя: в курсах, где он преподаёт, — все активности; где учится — только видимые студентам.
 */
@Component
class CourseEventsReader {

    private static final Set<CourseRole> STAFF = Set.of(CourseRole.TEACHER, CourseRole.ASSISTANT);
    private static final Set<CourseRole> LEARNERS = Set.of(CourseRole.STUDENT, CourseRole.OBSERVER, CourseRole.GUEST);
    private static final Set<ItemType> ACTIVITIES = Arrays.stream(ItemType.values()).filter(ItemType::isActivity)
            .collect(Collectors.toUnmodifiableSet());

    private final EnrollmentApi enrollment;
    private final CoursesApi courses;

    CourseEventsReader(EnrollmentApi enrollment, CoursesApi courses) {
        this.enrollment = enrollment;
        this.courses = courses;
    }

    List<CalendarEvent> events(UUID tenantId, UUID userId, Instant from, Instant to) {
        Set<UUID> staffCourses = new HashSet<>(enrollment.activeCourseIds(tenantId, userId, STAFF));
        Set<UUID> allCourses = new HashSet<>(staffCourses);
        allCourses.addAll(enrollment.activeCourseIds(tenantId, userId, LEARNERS));
        if (allCourses.isEmpty()) {
            return List.of();
        }
        Map<UUID, CourseRef> refs = courses.findCourses(tenantId, allCourses);
        List<ItemDates> dates = courses.itemsOfCourses(tenantId, allCourses, ACTIVITIES).stream()
                .filter(item -> staffCourses.contains(item.courseId()) || courses.isVisibleToLearners(tenantId, item))
                .map(item -> dates(item, refs.get(item.courseId())))
                .toList();
        return CourseCalendar.events(dates, from, to);
    }

    private static ItemDates dates(ItemRef item, CourseRef course) {
        return new ItemDates(item.id(), item.courseId(), item.title(), course == null ? null : course.title(), item.dueAt(),
                item.openAt(), item.closeAt());
    }
}
