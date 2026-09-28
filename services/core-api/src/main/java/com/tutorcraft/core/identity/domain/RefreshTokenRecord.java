package com.tutorcraft.core.identity.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Сохранённый refresh-токен (только хеш в БД, ADR-003). Семейство — цепочка ротаций одного входа:
 * предъявление уже отозванного токена означает кражу и отзывает всё семейство.
 */
public record RefreshTokenRecord(UUID id, UUID tenantId, UUID userId, UUID familyId, Instant expiresAt, Instant revokedAt) {

    public enum Decision { ROTATE, REUSE_DETECTED, EXPIRED }

    public Decision decide(Instant now) {
        if (revokedAt != null) {
            return Decision.REUSE_DETECTED;
        }
        if (!expiresAt.isAfter(now)) {
            return Decision.EXPIRED;
        }
        return Decision.ROTATE;
    }
}
