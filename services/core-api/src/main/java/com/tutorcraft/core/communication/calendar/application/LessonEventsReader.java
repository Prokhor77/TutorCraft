package com.tutorcraft.core.communication.calendar.application;

import com.tutorcraft.core.communication.calendar.domain.CalendarEvent;
import com.tutorcraft.core.communication.calendar.domain.CalendarEvent.Details;
import com.tutorcraft.core.communication.calendar.domain.CalendarEvent.Kind;
import com.tutorcraft.core.communication.calendar.domain.Lesson;
import com.tutorcraft.core.courses.CourseRef;
import com.tutorcraft.core.courses.CoursesApi;
import com.tutorcraft.core.courses.ItemRef;
import com.tutorcraft.core.courses.ModuleRef;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Занятия в календаре: преподавателю — все занятия его курсов; ученику — занятия всего курса и назначенные лично ему.
 * Ученику элемент курса показывается, только если он опубликован.
 */
@Component
class LessonEventsReader {

    private final LessonRepository lessons;
    private final CoursesApi courses;

    LessonEventsReader(LessonRepository lessons, CoursesApi courses) {
        this.lessons = lessons;
        this.courses = courses;
    }

    List<CalendarEvent> events(UUID tenantId, UUID userId, UserCourses userCourses, Instant from, Instant to) {
        List<Lesson> visible = lessons.inCourses(tenantId, userCourses.all(), from, to).stream()
                .filter(lesson -> userCourses.staff().contains(lesson.courseId()) || lesson.addressedTo(userId))
                .toList();
        if (visible.isEmpty()) {
            return List.of();
        }
        Titles titles = titles(tenantId, visible);
        return visible.stream().map(lesson -> toEvent(tenantId, lesson, userCourses, titles)).toList();
    }

    /** Занятие в виде события календаря (для ответа на создание/изменение — от лица того, кто его меняет). */
    CalendarEvent toStaffEvent(UUID tenantId, Lesson lesson) {
        UserCourses editor = new UserCourses(Set.of(lesson.courseId()), Set.of(),
                Set.of(lesson.courseId()));
        return toEvent(tenantId, lesson, editor, titles(tenantId, List.of(lesson)));
    }

    private CalendarEvent toEvent(UUID tenantId, Lesson lesson, UserCourses userCourses, Titles titles) {
        boolean staff = userCourses.staff().contains(lesson.courseId());
        ItemRef item = lesson.itemId() == null ? null : titles.items().get(lesson.itemId());
        boolean showItem = item != null && (staff || courses.isVisibleToLearners(tenantId, item));
        CourseRef course = titles.courses().get(lesson.courseId());
        Details details = new Details(lesson.description(), false, lesson.moduleId(),
                lesson.moduleId() == null ? null : titles.modules().get(lesson.moduleId()),
                showItem ? item.title() : null, lesson.audience(), staff ? lesson.attendeeIds() : List.of(),
                userCourses.editable().contains(lesson.courseId()), lesson.version());
        return new CalendarEvent(lesson.id(), lesson.title(), lesson.startsAt(), lesson.endsAt(), lesson.courseId(),
                showItem ? item.id() : null, Kind.LESSON, course == null ? null : course.title(), details);
    }

    private Titles titles(UUID tenantId, List<Lesson> lessons) {
        List<UUID> courseIds = lessons.stream().map(Lesson::courseId).distinct().toList();
        List<UUID> itemIds = lessons.stream().map(Lesson::itemId).filter(Objects::nonNull).distinct().toList();
        Map<UUID, String> modules = lessons.stream().filter(lesson -> lesson.moduleId() != null)
                .map(Lesson::courseId).distinct()
                .flatMap(courseId -> courses.modulesOfCourse(tenantId, courseId).stream())
                .collect(Collectors.toMap(ModuleRef::id, ModuleRef::title, (first, second) -> first));
        return new Titles(courses.findCourses(tenantId, courseIds),
                itemIds.isEmpty() ? Map.of() : courses.findItems(tenantId, itemIds), modules);
    }

    private record Titles(Map<UUID, CourseRef> courses, Map<UUID, ItemRef> items, Map<UUID, String> modules) {
    }
}
