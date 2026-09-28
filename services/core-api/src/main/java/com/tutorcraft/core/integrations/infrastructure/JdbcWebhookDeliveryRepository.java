package com.tutorcraft.core.integrations.infrastructure;

import com.tutorcraft.core.integrations.application.WebhookDeliveryRepository;
import com.tutorcraft.core.shared.api.CursorCodec;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.persistence.Jsonb;
import com.tutorcraft.core.shared.persistence.Timestamps;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcWebhookDeliveryRepository implements WebhookDeliveryRepository {

    private final JdbcClient jdbc;

    JdbcWebhookDeliveryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insertPending(UUID id, UUID tenantId, UUID webhookId, String eventName, String payloadJson, Instant now) {
        jdbc.sql("""
                INSERT INTO webhook_deliveries (id, tenant_id, webhook_id, event_name, payload, status, attempts,
                                                next_attempt_at, created_at)
                VALUES (:id, :tenantId, :webhookId, :event, :payload, 'pending', 0, :now, :now)
                """)
            .param("id", id).param("tenantId", tenantId).param("webhookId", webhookId).param("event", eventName)
            .param("payload", Jsonb.of(payloadJson)).param("now", Timestamps.of(now))
            .update();
    }

    @Override
    public List<ClaimedDelivery> claimDue(Instant now, Instant leaseUntil, int limit) {
        return jdbc.sql("""
                UPDATE webhook_deliveries d SET next_attempt_at = :leaseUntil
                FROM (SELECT id FROM webhook_deliveries
                      WHERE status = 'pending' AND next_attempt_at <= :now
                      ORDER BY next_attempt_at
                      LIMIT :limit
                      FOR UPDATE SKIP LOCKED) due
                WHERE d.id = due.id
                RETURNING d.id, d.tenant_id, d.webhook_id, d.event_name, d.payload::text AS payload, d.attempts
                """)
            .param("leaseUntil", Timestamps.of(leaseUntil)).param("now", Timestamps.of(now)).param("limit", limit)
            .query((rs, n) -> new ClaimedDelivery(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                    rs.getObject("webhook_id", UUID.class), rs.getString("event_name"), rs.getString("payload"),
                    rs.getInt("attempts")))
            .list();
    }

    @Override
    public void markSucceeded(UUID deliveryId, int attempts, Integer responseCode, Instant at) {
        finish(deliveryId, "succeeded", attempts, responseCode, null, at);
    }

    @Override
    public void markFailed(UUID deliveryId, int attempts, Integer responseCode, String error, Instant at) {
        finish(deliveryId, "failed", attempts, responseCode, error, at);
    }

    @Override
    public void markRetry(UUID deliveryId, int attempts, Integer responseCode, String error, Instant nextAttemptAt, Instant at) {
        jdbc.sql("""
                UPDATE webhook_deliveries SET attempts = :attempts, response_code = :code, error = :error,
                       last_attempt_at = :at, next_attempt_at = :next
                WHERE id = :id
                """)
            .param("attempts", attempts).param("code", responseCode).param("error", error)
            .param("at", Timestamps.of(at)).param("next", Timestamps.of(nextAttemptAt)).param("id", deliveryId)
            .update();
    }

    @Override
    public List<DeliveryView> page(UUID tenantId, UUID webhookId, PageQuery page) {
        CursorCodec.Position after = page.after().orElse(null);
        return jdbc.sql("""
                SELECT id, event_name, status, attempts, response_code, error, created_at, last_attempt_at, next_attempt_at
                FROM webhook_deliveries
                WHERE tenant_id = :tenantId AND webhook_id = :webhookId
                  AND (CAST(:afterAt AS timestamptz) IS NULL OR (created_at, id) < (:afterAt, :afterId))
                ORDER BY created_at DESC, id DESC
                LIMIT :limit
                """)
            .param("tenantId", tenantId).param("webhookId", webhookId)
            .param("afterAt", after == null ? null : Timestamps.of(after.sortKey()))
            .param("afterId", after == null ? null : after.id())
            .param("limit", page.fetchSize())
            .query((rs, n) -> new DeliveryView(rs.getObject("id", UUID.class), rs.getString("event_name"),
                    rs.getString("status"), rs.getInt("attempts"), rs.getObject("response_code", Integer.class),
                    rs.getString("error"), Timestamps.read(rs, "created_at"), Timestamps.read(rs, "last_attempt_at"),
                    Timestamps.read(rs, "next_attempt_at")))
            .list();
    }

    private void finish(UUID deliveryId, String status, int attempts, Integer responseCode, String error, Instant at) {
        jdbc.sql("""
                UPDATE webhook_deliveries SET status = :status, attempts = :attempts, response_code = :code, error = :error,
                       last_attempt_at = :at
                WHERE id = :id
                """)
            .param("status", status).param("attempts", attempts).param("code", responseCode).param("error", error)
            .param("at", Timestamps.of(at)).param("id", deliveryId)
            .update();
    }
}
