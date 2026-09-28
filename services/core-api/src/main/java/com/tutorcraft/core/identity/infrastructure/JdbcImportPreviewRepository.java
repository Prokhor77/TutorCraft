package com.tutorcraft.core.identity.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.tutorcraft.core.identity.application.ImportPreviewRepository;
import com.tutorcraft.core.shared.persistence.JsonCodec;
import com.tutorcraft.core.shared.persistence.Timestamps;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcImportPreviewRepository implements ImportPreviewRepository {

    private static final TypeReference<List<PreviewRow>> ROWS = new TypeReference<>() { };

    private final JdbcClient jdbc;
    private final JsonCodec json;

    JdbcImportPreviewRepository(JdbcClient jdbc, JsonCodec json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void insert(UUID id, UUID tenantId, UUID createdBy, List<PreviewRow> rows, Instant createdAt, Instant expiresAt) {
        jdbc.sql("""
                INSERT INTO user_import_previews (id, tenant_id, created_by, rows, created_at, expires_at)
                VALUES (:id, :tenantId, :createdBy, :rows, :createdAt, :expiresAt)
                """)
            .param("id", id).param("tenantId", tenantId).param("createdBy", createdBy)
            .param("rows", json.toJsonb(rows))
            .param("createdAt", Timestamps.of(createdAt)).param("expiresAt", Timestamps.of(expiresAt))
            .update();
    }

    @Override
    public Optional<List<PreviewRow>> findActive(UUID tenantId, UUID previewId, UUID createdBy, Instant now) {
        return jdbc.sql("""
                SELECT rows::text AS rows FROM user_import_previews
                WHERE tenant_id = :tenantId AND id = :id AND created_by = :createdBy
                  AND committed_at IS NULL AND expires_at > :now
                """)
            .param("tenantId", tenantId).param("id", previewId).param("createdBy", createdBy)
            .param("now", Timestamps.of(now))
            .query((rs, n) -> json.read(rs.getString("rows"), ROWS))
            .optional();
    }

    @Override
    public boolean markCommitted(UUID tenantId, UUID previewId, Instant now) {
        return jdbc.sql("""
                UPDATE user_import_previews SET committed_at = :now
                WHERE tenant_id = :tenantId AND id = :id AND committed_at IS NULL
                """)
            .param("now", Timestamps.of(now)).param("tenantId", tenantId).param("id", previewId)
            .update() == 1;
    }
}
