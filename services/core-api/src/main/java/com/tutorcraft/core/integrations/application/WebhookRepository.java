package com.tutorcraft.core.integrations.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Подписки на вебхуки. Секрет хранится зашифрованным (NFR-SEC-06). */
public interface WebhookRepository {

    void insert(NewWebhook webhook);

    List<WebhookSummary> list(UUID tenantId);

    boolean exists(UUID tenantId, UUID webhookId);

    /** Мягкое удаление; журнал доставок сохраняется. @return false, если не найден */
    boolean delete(UUID tenantId, UUID webhookId, Instant at);

    /** Активные подписки tenant на событие. */
    List<UUID> subscribedTo(UUID tenantId, String eventName);

    /** Данные для отправки; пусто, если вебхук удалён. */
    Optional<WebhookEndpoint> endpoint(UUID tenantId, UUID webhookId);

    record NewWebhook(UUID id, UUID tenantId, String url, List<String> events, String secretEncrypted, UUID createdBy,
                      Instant createdAt) {
    }

    record WebhookSummary(UUID id, String url, List<String> events, Instant createdAt) {
    }

    record WebhookEndpoint(UUID id, String url, String secretEncrypted) {
    }
}
