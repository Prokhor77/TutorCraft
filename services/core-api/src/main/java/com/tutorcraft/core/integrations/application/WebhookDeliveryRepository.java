package com.tutorcraft.core.integrations.application;

import com.tutorcraft.core.shared.api.PageQuery;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Журнал доставки вебхуков: очередь pending + история попыток. */
public interface WebhookDeliveryRepository {

    void insertPending(UUID id, UUID tenantId, UUID webhookId, String eventName, String payloadJson, Instant now);

    /**
     * Захват пачки просроченных pending-доставок: next_attempt_at сдвигается на leaseUntil (FOR UPDATE SKIP LOCKED),
     * поэтому параллельные экземпляры не отправят одну доставку дважды.
     */
    List<ClaimedDelivery> claimDue(Instant now, Instant leaseUntil, int limit);

    void markSucceeded(UUID deliveryId, int attempts, Integer responseCode, Instant at);

    void markRetry(UUID deliveryId, int attempts, Integer responseCode, String error, Instant nextAttemptAt, Instant at);

    void markFailed(UUID deliveryId, int attempts, Integer responseCode, String error, Instant at);

    List<DeliveryView> page(UUID tenantId, UUID webhookId, PageQuery page);

    record ClaimedDelivery(UUID id, UUID tenantId, UUID webhookId, String eventName, String payloadJson, int attempts) {
    }

    record DeliveryView(UUID id, String event, String status, int attempts, Integer responseCode, String error,
                        Instant createdAt, Instant lastAttemptAt, Instant nextAttemptAt) {
    }
}
