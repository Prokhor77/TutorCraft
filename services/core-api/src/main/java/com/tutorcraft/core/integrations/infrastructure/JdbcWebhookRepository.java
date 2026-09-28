package com.tutorcraft.core.integrations.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.tutorcraft.core.integrations.application.WebhookRepository;
import com.tutorcraft.core.shared.persistence.JsonCodec;
import com.tutorcraft.core.shared.persistence.Timestamps;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcWebhookRepository implements WebhookRepository {

    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() { };

    private final JdbcClient jdbc;
    private final JsonCodec json;

    JdbcWebhookRepository(JdbcClient jdbc, JsonCodec json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void insert(NewWebhook webhook) {
        jdbc.sql("""
                INSERT INTO webhooks (id, tenant_id, url, events, secret_encrypted, created_by, created_at)
                VALUES (:id, :tenantId, :url, :events, :secret, :createdBy, :createdAt)
                """)
            .param("id", webhook.id()).param("tenantId", webhook.tenantId()).param("url", webhook.url())
            .param("events", json.toJsonb(webhook.events())).param("secret", webhook.secretEncrypted())
            .param("createdBy", webhook.createdBy()).param("createdAt", Timestamps.of(webhook.createdAt()))
            .update();
    }

    @Override
    public List<WebhookSummary> list(UUID tenantId) {
        return jdbc.sql("""
                SELECT id, url, events::text AS events, created_at FROM webhooks
                WHERE tenant_id = :tenantId AND deleted_at IS NULL
                ORDER BY created_at DESC
                """)
            .param("tenantId", tenantId)
            .query((rs, n) -> new WebhookSummary(rs.getObject("id", UUID.class), rs.getString("url"),
                    json.read(rs.getString("events"), STRINGS), Timestamps.read(rs, "created_at")))
            .list();
    }

    @Override
    public boolean exists(UUID tenantId, UUID webhookId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM webhooks WHERE tenant_id = :tenantId AND id = :id)")
            .param("tenantId", tenantId).param("id", webhookId)
            .query(Boolean.class).single();
    }

    @Override
    public boolean delete(UUID tenantId, UUID webhookId, Instant at) {
        return jdbc.sql("UPDATE webhooks SET deleted_at = :at WHERE tenant_id = :tenantId AND id = :id AND deleted_at IS NULL")
            .param("at", Timestamps.of(at)).param("tenantId", tenantId).param("id", webhookId)
            .update() == 1;
    }

    @Override
    public List<UUID> subscribedTo(UUID tenantId, String eventName) {
        return jdbc.sql("""
                SELECT id FROM webhooks
                WHERE tenant_id = :tenantId AND deleted_at IS NULL AND events @> jsonb_build_array(CAST(:event AS text))
                """)
            .param("tenantId", tenantId).param("event", eventName)
            .query(UUID.class).list();
    }

    @Override
    public Optional<WebhookEndpoint> endpoint(UUID tenantId, UUID webhookId) {
        return jdbc.sql("""
                SELECT id, url, secret_encrypted FROM webhooks
                WHERE tenant_id = :tenantId AND id = :id AND deleted_at IS NULL
                """)
            .param("tenantId", tenantId).param("id", webhookId)
            .query((rs, n) -> new WebhookEndpoint(rs.getObject("id", UUID.class), rs.getString("url"),
                    rs.getString("secret_encrypted")))
            .optional();
    }
}
