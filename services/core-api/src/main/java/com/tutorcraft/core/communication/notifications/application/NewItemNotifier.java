package com.tutorcraft.core.communication.notifications.application;

import com.tutorcraft.core.communication.NotificationCategory;
import com.tutorcraft.core.communication.NotificationsApi;
import com.tutorcraft.core.communication.NotificationsApi.NotificationCommand;
import com.tutorcraft.core.courses.CourseEvents.ChangeKind;
import com.tutorcraft.core.courses.CourseEvents.ItemChanged;
import com.tutorcraft.core.courses.CourseRef;
import com.tutorcraft.core.courses.CoursesApi;
import com.tutorcraft.core.courses.ItemRef;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * «Новый элемент» (FR-NOTIF-01): когда элемент создан или изменён и виден студентам — уведомление активным студентам
 * курса один раз на элемент (dedupe new_item:{itemId}), даже если его потом скрывали и снова открывали.
 */
@Component
class NewItemNotifier {

    static final String MESSAGE_CODE = "notifications.new_item";
    private static final Set<ChangeKind> RELEVANT = Set.of(ChangeKind.CREATED, ChangeKind.UPDATED, ChangeKind.RESTORED);
    private static final String DEDUPE_PREFIX = "new_item:";

    private final CoursesApi courses;
    private final CourseAudience audience;
    private final NotificationsApi notifications;

    NewItemNotifier(CoursesApi courses, CourseAudience audience, NotificationsApi notifications) {
        this.courses = courses;
        this.audience = audience;
        this.notifications = notifications;
    }

    /** События courses публикуются после записи в MongoDB — уведомления фиксируются в собственной транзакции. */
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onItemChanged(ItemChanged event) {
        if (!RELEVANT.contains(event.kind())) {
            return;
        }
        ItemRef item = courses.findItems(event.tenantId(), List.of(event.itemId())).get(event.itemId());
        if (item == null || !courses.isVisibleToLearners(event.tenantId(), item)) {
            return;
        }
        List<UUID> students = audience.activeStudents(event.tenantId(), event.courseId());
        if (students.isEmpty()) {
            return;
        }
        String courseTitle = courses.findCourse(event.tenantId(), event.courseId()).map(CourseRef::title).orElse("");
        notifications.notify(NotificationCommand.of(event.tenantId(), students, NotificationCategory.NEW_ITEM, MESSAGE_CODE,
                List.<Object>of(item.title(), courseTitle),
                DeadlineReminderService.LINK_TEMPLATE.formatted(event.courseId(), event.itemId()),
                DEDUPE_PREFIX + event.itemId()));
    }
}
