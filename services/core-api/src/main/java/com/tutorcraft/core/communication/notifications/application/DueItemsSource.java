package com.tutorcraft.core.communication.notifications.application;

import com.tutorcraft.core.courses.ItemRef;
import java.time.Instant;
import java.util.List;

/**
 * Порт: элементы курсов всех tenant со сроком сдачи в интервале (from, to] — для напоминаний о дедлайнах (FR-NOTIF-01).
 * Реализация — {@code infrastructure.CoursesDueItemsSource} поверх {@code CoursesApi.itemsDueBetween}; видимость
 * студентам проверяет {@link DeadlineReminderService} в момент отправки.
 */
public interface DueItemsSource {

    List<ItemRef> itemsDueBetween(Instant fromExclusive, Instant toInclusive);
}
