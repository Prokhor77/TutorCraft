package com.tutorcraft.core.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefreshTokenRecordTest {

    private static final Instant NOW = Instant.parse("2026-09-27T18:00:00Z");

    @Test
    void activeUnexpiredTokenIsRotated() {
        assertThat(token(NOW.plusSeconds(60), null).decide(NOW)).isEqualTo(RefreshTokenRecord.Decision.ROTATE);
    }

    @Test
    void revokedTokenMeansReuse() {
        assertThat(token(NOW.plusSeconds(60), NOW.minusSeconds(5)).decide(NOW))
                .isEqualTo(RefreshTokenRecord.Decision.REUSE_DETECTED);
    }

    @Test
    void reuseDetectionWinsOverExpiry() {
        assertThat(token(NOW.minusSeconds(60), NOW.minusSeconds(120)).decide(NOW))
                .isEqualTo(RefreshTokenRecord.Decision.REUSE_DETECTED);
    }

    @Test
    void tokenExpiringNowIsExpired() {
        assertThat(token(NOW, null).decide(NOW)).isEqualTo(RefreshTokenRecord.Decision.EXPIRED);
    }

    private static RefreshTokenRecord token(Instant expiresAt, Instant revokedAt) {
        return new RefreshTokenRecord(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), expiresAt, revokedAt);
    }
}
