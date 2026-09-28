package com.tutorcraft.core.gradebook.application;

import com.tutorcraft.core.communication.NotificationCategory;
import com.tutorcraft.core.communication.NotificationsApi;
import com.tutorcraft.core.communication.NotificationsApi.NotificationCommand;
import com.tutorcraft.core.courses.CourseRef;
import com.tutorcraft.core.courses.CoursesApi;
import com.tutorcraft.core.gradebook.GradebookEvents.GradeChanged;
import com.tutorcraft.core.gradebook.domain.GradeColumn;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Уведомление студенту об опубликованной оценке (FR-NOTIF-01, категория grade_published).
 * Ключ дедупликации включает балл: повторная публикация того же балла не дублирует уведомление, изменение — сообщает.
 */
@Component
class GradePublishedNotifier {

    static final String MESSAGE_CODE = "gradebook.grade_published";
    private static final String LINK_TEMPLATE = "/courses/%s/grades";
    private static final String DEDUPE_PREFIX = "grade_published:";
    private static final String SEPARATOR = ":";

    private final GradebookStructureRepository structure;
    private final CoursesApi courses;
    private final NotificationsApi notifications;

    GradePublishedNotifier(GradebookStructureRepository structure, CoursesApi courses, NotificationsApi notifications) {
        this.structure = structure;
        this.courses = courses;
        this.notifications = notifications;
    }

    /** Синхронно в транзакции изменения оценки. */
    @EventListener
    public void onGradeChanged(GradeChanged event) {
        if (!event.published() || event.score() == null || event.sourceItemId() == null) {
            return;
        }
        structure.columnBySource(event.tenantId(), event.sourceItemId())
                .ifPresent(column -> notifyStudent(event.tenantId(), column, event.userId(), event.score()));
    }

    /** Для ручных столбцов (без элемента-источника событие GradeChanged не публикуется). */
    void notifyStudent(UUID tenantId, GradeColumn column, UUID userId, BigDecimal score) {
        String courseTitle = courses.findCourse(tenantId, column.courseId()).map(CourseRef::title).orElse("");
        notifications.notify(NotificationCommand.of(tenantId, List.of(userId), NotificationCategory.GRADE_PUBLISHED,
                MESSAGE_CODE, List.<Object>of(column.name(), courseTitle, plain(score), plain(column.maxScore())),
                LINK_TEMPLATE.formatted(column.courseId()),
                DEDUPE_PREFIX + column.id() + SEPARATOR + userId + SEPARATOR + plain(score)));
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
