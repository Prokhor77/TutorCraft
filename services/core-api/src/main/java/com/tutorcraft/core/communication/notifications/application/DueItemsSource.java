package com.tutorcraft.core.communication.notifications.application;

import com.tutorcraft.core.courses.ItemRef;
import java.time.Instant;
import java.util.List;

/**
 * Порт: элементы курсов всех tenant со сроком сдачи в интервале (from, to] — для напоминаний о дедлайнах (FR-NOTIF-01).
 * Адаптер поверх {@code CoursesApi.itemsDueBetween(from, to)} (запрошено у модуля courses); пока его нет,
 * напоминания не отправляются (предупреждение в логе при старте).
 */
public interface DueItemsSource {

    List<ItemRef> itemsDueBetween(Instant fromExclusive, Instant toInclusive);
}
