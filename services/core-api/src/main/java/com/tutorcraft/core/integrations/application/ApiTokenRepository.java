package com.tutorcraft.core.integrations.application;

import com.tutorcraft.core.integrations.domain.ApiTokenScope;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Personal access tokens (только SHA-256-хеш, NFR-SEC-07). */
public interface ApiTokenRepository {

    void insert(NewApiToken token);

    List<ApiTokenSummary> list(UUID tenantId);

    /** @return false, если токен не найден в tenant или уже отозван */
    boolean revoke(UUID tenantId, UUID tokenId, Instant at);

    /** Неотозванный и не истёкший токен (поиск по хешу — глобальный: tenant берётся из токена). */
    Optional<ActiveApiToken> findActiveByHash(String tokenHash, Instant now);

    void touch(UUID tokenId, Instant at);

    record NewApiToken(UUID id, UUID tenantId, UUID userId, String name, String tokenHash, Set<ApiTokenScope> scopes,
                       Instant expiresAt, Instant createdAt) {
    }

    record ApiTokenSummary(UUID id, UUID userId, String name, List<String> scopes, Instant expiresAt, Instant lastUsedAt,
                           Instant createdAt) {
    }

    record ActiveApiToken(UUID id, UUID tenantId, UUID userId, Set<ApiTokenScope> scopes, Instant lastUsedAt) {
    }
}
