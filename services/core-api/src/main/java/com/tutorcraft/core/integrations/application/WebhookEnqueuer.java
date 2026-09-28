package com.tutorcraft.core.integrations.application;

import com.tutorcraft.core.integrations.domain.WebhookEvent;
import com.tutorcraft.core.shared.domain.Ids;
import com.tutorcraft.core.shared.outbox.OutboxPublisher;
import com.tutorcraft.core.shared.outbox.Topics;
import com.tutorcraft.core.shared.persistence.JsonCodec;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Постановка доменного события в очередь вебхуков и в tc.domain.events.v1 — в транзакции бизнес-операции,
 * поэтому событие не теряется и не публикуется при откате. Зависит только от репозиториев и shared-ядра.
 */
@Component
public class WebhookEnqueuer {

    private final WebhookRepository webhooks;
    private final WebhookDeliveryRepository deliveries;
    private final OutboxPublisher outbox;
    private final JsonCodec json;
    private final Clock clock;

    public WebhookEnqueuer(WebhookRepository webhooks, WebhookDeliveryRepository deliveries, OutboxPublisher outbox,
                           JsonCodec json, Clock clock) {
        this.webhooks = webhooks;
        this.deliveries = deliveries;
        this.outbox = outbox;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public void enqueue(UUID tenantId, WebhookEvent event, Map<String, Object> data) {
        outbox.publish(Topics.DOMAIN_EVENTS, tenantId, event.key(), Map.of("name", event.key(), "data", data));
        List<UUID> subscribers = webhooks.subscribedTo(tenantId, event.key());
        Instant now = clock.instant();
        for (UUID webhookId : subscribers) {
            UUID deliveryId = Ids.newId();
            deliveries.insertPending(deliveryId, tenantId, webhookId, event.key(), json.write(body(deliveryId, event, now, data)), now);
        }
    }

    /** Тело запроса к подписчику. */
    private static Map<String, Object> body(UUID deliveryId, WebhookEvent event, Instant now, Map<String, Object> data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", deliveryId.toString());
        body.put("event", event.key());
        body.put("occurredAt", now.toString());
        body.put("data", data);
        return body;
    }
}
