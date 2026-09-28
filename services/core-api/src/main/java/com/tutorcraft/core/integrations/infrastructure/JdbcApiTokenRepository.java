package com.tutorcraft.core.integrations.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.tutorcraft.core.integrations.application.ApiTokenRepository;
import com.tutorcraft.core.integrations.domain.ApiTokenScope;
import com.tutorcraft.core.shared.persistence.JsonCodec;
import com.tutorcraft.core.shared.persistence.Timestamps;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcApiTokenRepository implements ApiTokenRepository {

    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() { };

    private final JdbcClient jdbc;
    private final JsonCodec json;

    JdbcApiTokenRepository(JdbcClient jdbc, JsonCodec json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void insert(NewApiToken token) {
        jdbc.sql("""
                INSERT INTO api_tokens (id, tenant_id, user_id, name, token_hash, scopes, expires_at, created_at)
                VALUES (:id, :tenantId, :userId, :name, :hash, :scopes, :expiresAt, :createdAt)
                """)
            .param("id", token.id()).param("tenantId", token.tenantId()).param("userId", token.userId())
            .param("name", token.name()).param("hash", token.tokenHash())
            .param("scopes", json.toJsonb(token.scopes().stream().map(ApiTokenScope::key).sorted().toList()))
            .param("expiresAt", Timestamps.of(token.expiresAt())).param("createdAt", Timestamps.of(token.createdAt()))
            .update();
    }

    @Override
    public List<ApiTokenSummary> list(UUID tenantId) {
        return jdbc.sql("""
                SELECT id, user_id, name, scopes::text AS scopes, expires_at, last_used_at, created_at FROM api_tokens
                WHERE tenant_id = :tenantId AND revoked_at IS NULL
                ORDER BY created_at DESC
                """)
            .param("tenantId", tenantId)
            .query((rs, n) -> new ApiTokenSummary(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                    rs.getString("name"), json.read(rs.getString("scopes"), STRINGS), Timestamps.read(rs, "expires_at"),
                    Timestamps.read(rs, "last_used_at"), Timestamps.read(rs, "created_at")))
            .list();
    }

    @Override
    public boolean revoke(UUID tenantId, UUID tokenId, Instant at) {
        return jdbc.sql("UPDATE api_tokens SET revoked_at = :at WHERE tenant_id = :tenantId AND id = :id AND revoked_at IS NULL")
            .param("at", Timestamps.of(at)).param("tenantId", tenantId).param("id", tokenId)
            .update() == 1;
    }

    @Override
    public Optional<ActiveApiToken> findActiveByHash(String tokenHash, Instant now) {
        return jdbc.sql("""
                SELECT id, tenant_id, user_id, scopes::text AS scopes, last_used_at FROM api_tokens
                WHERE token_hash = :hash AND revoked_at IS NULL AND (expires_at IS NULL OR expires_at > :now)
                """)
            .param("hash", tokenHash).param("now", Timestamps.of(now))
            .query((rs, n) -> new ActiveApiToken(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                    rs.getObject("user_id", UUID.class), scopes(rs.getString("scopes")), Timestamps.read(rs, "last_used_at")))
            .optional();
    }

    @Override
    public void touch(UUID tokenId, Instant at) {
        jdbc.sql("UPDATE api_tokens SET last_used_at = :at WHERE id = :id")
            .param("at", Timestamps.of(at)).param("id", tokenId)
            .update();
    }

    private Set<ApiTokenScope> scopes(String raw) {
        return json.read(raw, STRINGS).stream()
                .map(ApiTokenScope::find)
                .flatMap(Optional::stream)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ApiTokenScope.class)));
    }
}
