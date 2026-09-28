package com.tutorcraft.core.communication.notifications.application;

import com.tutorcraft.core.communication.notifications.domain.NotificationChannel;
import com.tutorcraft.core.shared.outbox.ProcessedEvents;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Статусы доставки от notifier (tc.notify.delivered.v1). Повторные сообщения игнорируются по eventId. */
@Service
public class DeliveryStatusService {

    static final String CONSUMER = "notifications.delivery-status";
    private static final Set<String> STATUSES = Set.of("sent", "failed", "skipped");

    private final NotificationRepository notifications;
    private final ProcessedEvents processedEvents;
    private final Clock clock;

    DeliveryStatusService(NotificationRepository notifications, ProcessedEvents processedEvents, Clock clock) {
        this.notifications = notifications;
        this.processedEvents = processedEvents;
        this.clock = clock;
    }

    @Transactional
    public void record(UUID eventId, DeliveryReport report) {
        if (!processedEvents.markProcessed(eventId, CONSUMER)) {
            return;
        }
        boolean externalChannel = NotificationChannel.find(report.channel()).map(NotificationChannel::external).orElse(false);
        if (report.notificationId() == null || !externalChannel || !STATUSES.contains(report.status())) {
            return;
        }
        notifications.updateDelivery(report.notificationId(), report.channel(), report.status(), report.error(), clock.instant());
    }

    /** payload tc.notify.delivered.v1. */
    public record DeliveryReport(UUID notificationId, String channel, String status, String error) {
    }
}
