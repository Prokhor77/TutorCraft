package com.tutorcraft.core.communication.calendar.application;

import com.tutorcraft.core.communication.calendar.domain.CalendarEvent;
import com.tutorcraft.core.communication.calendar.domain.CourseCalendar;
import com.tutorcraft.core.communication.calendar.domain.CourseCalendar.ItemDates;
import com.tutorcraft.core.courses.CourseRef;
import com.tutorcraft.core.courses.CoursesApi;
import com.tutorcraft.core.courses.ItemRef;
import com.tutorcraft.core.courses.ItemType;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Ключевые даты активностей курсов пользователя: в курсах, где он преподаёт, — все активности; где учится — только
 * видимые студентам.
 */
@Component
class CourseEventsReader {

    private static final Set<ItemType> ACTIVITIES = Arrays.stream(ItemType.values()).filter(ItemType::isActivity)
            .collect(Collectors.toUnmodifiableSet());

    private final CoursesApi courses;

    CourseEventsReader(CoursesApi courses) {
        this.courses = courses;
    }

    List<CalendarEvent> events(UUID tenantId, UserCourses userCourses, Instant from, Instant to) {
        if (userCourses.isEmpty()) {
            return List.of();
        }
        Set<UUID> allCourses = userCourses.all();
        Map<UUID, CourseRef> refs = courses.findCourses(tenantId, allCourses);
        List<ItemDates> dates = courses.itemsOfCourses(tenantId, allCourses, ACTIVITIES).stream()
                .filter(item -> userCourses.staff().contains(item.courseId()) || courses.isVisibleToLearners(tenantId, item))
                .map(item -> dates(item, refs.get(item.courseId())))
                .toList();
        return CourseCalendar.events(dates, from, to);
    }

    private static ItemDates dates(ItemRef item, CourseRef course) {
        return new ItemDates(item.id(), item.courseId(), item.title(), course == null ? null : course.title(), item.dueAt(),
                item.openAt(), item.closeAt());
    }
}
