package com.tutorcraft.core.identity.application;

import com.tutorcraft.core.identity.domain.RefreshTokenRecord;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Refresh-токены (только SHA-256-хеш). Поиск по хешу — глобальный: у refresh-запроса нет tenant-контекста. */
public interface RefreshTokenRepository {

    void insert(NewRefreshToken token);

    Optional<RefreshTokenRecord> findByHash(String tokenHash);

    /** Атомарно отзывает активный токен. @return false, если токен уже отозван (гонка или повторное использование). */
    boolean revokeIfActive(UUID tokenId, Instant now, UUID replacedBy);

    int revokeFamily(UUID familyId, Instant now);

    int revokeAllOfUser(UUID tenantId, UUID userId, Instant now);

    record NewRefreshToken(UUID id, UUID tenantId, UUID userId, UUID familyId, String tokenHash,
                           Instant createdAt, Instant expiresAt) {
    }
}
