package com.tutorcraft.core.communication.notifications.application;

import com.tutorcraft.core.communication.NotificationCategory;
import com.tutorcraft.core.communication.NotificationsApi;
import com.tutorcraft.core.communication.NotificationsApi.NotificationCommand;
import com.tutorcraft.core.courses.CourseRef;
import com.tutorcraft.core.courses.CoursesApi;
import com.tutorcraft.core.courses.ItemRef;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Напоминание о приближении срока (FR-NOTIF-01): за 24 ч и за 1 ч, только тем, кто ещё не сдал. */
@Service
public class DeadlineReminderService {

    static final String LINK_TEMPLATE = "/courses/%s/items/%s";
    private static final String MESSAGE_PREFIX = "notifications.deadline_";
    private static final String DEDUPE_TEMPLATE = "deadline:%s:%s:%s";

    /** Интервалы напоминаний; label — часть ключа дедупликации и кода сообщения. */
    public enum Reminder {
        DAY_BEFORE(Duration.ofHours(24), "24h"), HOUR_BEFORE(Duration.ofHours(1), "1h");

        private final Duration offset;
        private final String label;

        Reminder(Duration offset, String label) {
            this.offset = offset;
            this.label = label;
        }

        public Duration offset() {
            return offset;
        }

        public String label() {
            return label;
        }
    }

    private final CoursesApi courses;
    private final CourseAudience audience;
    private final NotificationsApi notifications;

    DeadlineReminderService(CoursesApi courses, CourseAudience audience, NotificationsApi notifications) {
        this.courses = courses;
        this.audience = audience;
        this.notifications = notifications;
    }

    @Transactional
    public int remind(ItemRef item, Reminder reminder) {
        if (!item.type().isActivity() || item.dueAt() == null || !courses.isVisibleToLearners(item.tenantId(), item)) {
            return 0;
        }
        String courseTitle = courses.findCourse(item.tenantId(), item.courseId()).map(CourseRef::title).orElse("");
        List<UUID> recipients = audience.studentsWithPendingWork(item);
        recipients.forEach(userId -> notifications.notify(NotificationCommand.of(item.tenantId(), List.of(userId),
                NotificationCategory.DEADLINE, MESSAGE_PREFIX + reminder.label(), List.<Object>of(item.title(), courseTitle),
                LINK_TEMPLATE.formatted(item.courseId(), item.id()),
                DEDUPE_TEMPLATE.formatted(item.id(), userId, reminder.label()))));
        return recipients.size();
    }
}
