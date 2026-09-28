package com.tutorcraft.core.communication.notifications.application;

import com.tutorcraft.core.communication.notifications.application.DeadlineReminderService.Reminder;
import com.tutorcraft.core.courses.ItemRef;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Каждые {@value #SCAN_INTERVAL_MINUTES} мин ищет элементы со сроком в окне (now + offset − интервал, now + offset]
 * для каждого напоминания. Повторы при нескольких экземплярах и перезапусках гасит дедупликация уведомлений.
 */
@Component
class DeadlineReminderScheduler {

    static final long SCAN_INTERVAL_MINUTES = 5;
    private static final Duration SCAN_WINDOW = Duration.ofMinutes(SCAN_INTERVAL_MINUTES);
    private static final Logger log = LoggerFactory.getLogger(DeadlineReminderScheduler.class);

    private final ObjectProvider<DueItemsSource> sources;
    private final DeadlineReminderService reminders;
    private final Clock clock;

    DeadlineReminderScheduler(ObjectProvider<DueItemsSource> sources, DeadlineReminderService reminders, Clock clock) {
        this.sources = sources;
        this.reminders = reminders;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    void warnIfSourceMissing() {
        if (sources.getIfAvailable() == null) {
            log.warn("No DueItemsSource bean: deadline reminders are disabled until CoursesApi.itemsDueBetween is wired");
        }
    }

    @Scheduled(fixedRate = SCAN_INTERVAL_MINUTES, timeUnit = TimeUnit.MINUTES)
    void scan() {
        DueItemsSource source = sources.getIfAvailable();
        if (source == null) {
            return;
        }
        Instant now = clock.instant();
        for (Reminder reminder : Reminder.values()) {
            Instant windowEnd = now.plus(reminder.offset());
            source.itemsDueBetween(windowEnd.minus(SCAN_WINDOW), windowEnd).forEach(item -> remindSafely(item, reminder));
        }
    }

    /** Ошибка по одному элементу не должна останавливать напоминания по остальным. */
    private void remindSafely(ItemRef item, Reminder reminder) {
        try {
            int sent = reminders.remind(item, reminder);
            log.debug("Deadline reminder {} for item {}: {} recipients", reminder.label(), item.id(), sent);
        } catch (RuntimeException e) {
            log.warn("Deadline reminder {} for item {} failed: {}", reminder.label(), item.id(), e.getClass().getSimpleName());
        }
    }
}
