package com.tutorcraft.core.courses.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.tutorcraft.core.courses.application.EmbedWhitelistProvider;
import com.tutorcraft.core.shared.persistence.JsonCodec;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Белый список встраиваний из настроек tenant (tenants.embed_whitelist, схема модуля org — только чтение).
 * OrgApi.TenantInfo это поле не содержит, поэтому читаем колонку напрямую.
 */
@Component
class JdbcEmbedWhitelistProvider implements EmbedWhitelistProvider {

    private static final TypeReference<List<String>> HOSTS = new TypeReference<>() {
    };

    private final JdbcClient jdbc;
    private final JsonCodec json;

    JdbcEmbedWhitelistProvider(JdbcClient jdbc, JsonCodec json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Set<String> embedWhitelist(UUID tenantId) {
        return jdbc.sql("SELECT embed_whitelist::text FROM tenants WHERE id = :tenantId")
            .param("tenantId", tenantId)
            .query(String.class)
            .optional()
            .map(raw -> json.read(raw, HOSTS))
            .<Set<String>>map(Set::copyOf)
            .orElse(Set.of());
    }
}
