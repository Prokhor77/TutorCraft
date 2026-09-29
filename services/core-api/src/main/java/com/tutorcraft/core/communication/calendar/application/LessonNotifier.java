package com.tutorcraft.core.communication.calendar.application;

import com.tutorcraft.core.communication.NotificationCategory;
import com.tutorcraft.core.communication.NotificationsApi;
import com.tutorcraft.core.communication.NotificationsApi.NotificationCommand;
import com.tutorcraft.core.communication.calendar.domain.Lesson;
import com.tutorcraft.core.courses.CourseRef;
import com.tutorcraft.core.courses.CoursesApi;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Уведомления учеников о занятиях (категория «объявления»): назначено, перенесено, отменено.
 * Вызывается в транзакции изменения занятия (outbox). Идемпотентно по dedupeKey.
 */
@Component
class LessonNotifier {

    static final String SCHEDULED = "calendar.lesson_scheduled";
    static final String RESCHEDULED = "calendar.lesson_rescheduled";
    static final String CANCELLED = "calendar.lesson_cancelled";
    static final String LINK = "/calendar";
    private static final String DEDUPE_TEMPLATE = "lesson:%s:%s:%d";

    private final NotificationsApi notifications;
    private final CoursesApi courses;

    LessonNotifier(NotificationsApi notifications, CoursesApi courses) {
        this.notifications = notifications;
        this.courses = courses;
    }

    void created(Lesson lesson, Set<UUID> activeStudents) {
        send(lesson, lesson.recipients(activeStudents), SCHEDULED);
    }

    /** Новым ученикам — «назначено», оставшимся при смене времени — «перенесено», исключённым — «отменено». */
    void updated(Lesson previous, Lesson current, Set<UUID> activeStudents) {
        List<UUID> before = previous.recipients(activeStudents);
        List<UUID> after = current.recipients(activeStudents);
        send(current, after.stream().filter(userId -> !before.contains(userId)).toList(), SCHEDULED);
        send(previous.withVersion(current.version()), before.stream().filter(userId -> !after.contains(userId)).toList(),
                CANCELLED);
        if (current.rescheduledFrom(previous)) {
            send(current, after.stream().filter(before::contains).toList(), RESCHEDULED);
        }
    }

    void deleted(Lesson lesson, Set<UUID> activeStudents) {
        send(lesson.withVersion(lesson.version() + 1), lesson.recipients(activeStudents), CANCELLED);
    }

    private void send(Lesson lesson, Collection<UUID> recipients, String messageCode) {
        if (recipients.isEmpty()) {
            return;
        }
        String courseTitle = courses.findCourse(lesson.tenantId(), lesson.courseId()).map(CourseRef::title).orElse("");
        notifications.notify(NotificationCommand.of(lesson.tenantId(), recipients, NotificationCategory.ANNOUNCEMENT,
                messageCode, List.<Object>of(lesson.title(), courseTitle), LINK,
                DEDUPE_TEMPLATE.formatted(lesson.id(), messageCode, lesson.version())));
    }
}
