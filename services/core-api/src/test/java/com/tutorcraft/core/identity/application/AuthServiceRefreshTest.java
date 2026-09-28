package com.tutorcraft.core.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tutorcraft.core.audit.AuditLog;
import com.tutorcraft.core.audit.AuditRecord;
import com.tutorcraft.core.identity.domain.RefreshTokenRecord;
import com.tutorcraft.core.identity.domain.UserAccount;
import com.tutorcraft.core.identity.domain.UserStatus;
import com.tutorcraft.core.shared.domain.UnauthorizedException;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import com.tutorcraft.core.shared.security.TokenHasher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Ротация refresh-токена и обнаружение повторного использования (ADR-003). */
class AuthServiceRefreshTest {

    private static final Instant NOW = Instant.parse("2026-09-27T18:00:00Z");
    private static final String RAW_TOKEN = "raw-refresh-token";

    private final SessionService sessions = mock(SessionService.class);
    private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final SignInGuard signInGuard = mock(SignInGuard.class);
    private final MeViewAssembler meAssembler = mock(MeViewAssembler.class);
    private final AuditLog audit = mock(AuditLog.class);
    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID familyId = UUID.randomUUID();
    private AuthService auth;
    private UserAccount user;

    @BeforeEach
    void setUp() {
        auth = new AuthService(mock(AccountProvisioner.class), sessions, refreshTokens, users, signInGuard, meAssembler,
                mock(CurrentUserProvider.class), audit, Clock.fixed(NOW, ZoneOffset.UTC));
        user = new UserAccount(userId, tenantId, "u@school.ru", "hash", "U", "Ser", null, "Europe/Moscow", "ru",
                UserStatus.ACTIVE, null, null, null, null, NOW, 0);
        when(users.findById(tenantId, userId)).thenReturn(Optional.of(user));
    }

    @Test
    void validTokenIsRotatedWithinItsFamily() {
        RefreshTokenRecord token = stored(null, NOW.plusSeconds(3600));
        IssuedSession next = new IssuedSession("access", 900, "next-refresh", Duration.ofDays(30), user);
        when(sessions.rotate(token, user)).thenReturn(next);

        AuthResult result = auth.refresh(RAW_TOKEN);

        assertThat(result.session()).isSameAs(next);
        verify(sessions, never()).revokeFamily(any());
    }

    @Test
    void reusedRevokedTokenRevokesWholeFamilyAndIsAudited() {
        stored(NOW.minusSeconds(10), NOW.plusSeconds(3600));

        assertThatThrownBy(() -> auth.refresh(RAW_TOKEN))
                .isInstanceOf(UnauthorizedException.class)
                .extracting("code").isEqualTo(IdentityErrors.REFRESH_INVALID);

        verify(sessions).revokeFamily(familyId);
        verify(sessions, never()).rotate(any(), any());
        ArgumentCaptor<AuditRecord> record = ArgumentCaptor.forClass(AuditRecord.class);
        verify(audit).record(record.capture());
        assertThat(record.getValue().action()).isEqualTo("auth.refresh_reuse_detected");
    }

    @Test
    void concurrentRotationIsTreatedAsReuse() {
        RefreshTokenRecord token = stored(null, NOW.plusSeconds(3600));
        when(sessions.rotate(token, user)).thenThrow(new SessionService.RefreshReuseException());

        assertThatThrownBy(() -> auth.refresh(RAW_TOKEN)).isInstanceOf(UnauthorizedException.class);

        verify(sessions).revokeFamily(familyId);
    }

    @Test
    void expiredTokenIsRejectedWithoutRevokingFamily() {
        stored(null, NOW.minusSeconds(1));

        assertThatThrownBy(() -> auth.refresh(RAW_TOKEN)).isInstanceOf(UnauthorizedException.class);

        verify(sessions, never()).revokeFamily(any());
        verify(sessions, never()).rotate(any(), any());
    }

    @Test
    void unknownOrMissingTokenIsRejected() {
        when(refreshTokens.findByHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auth.refresh(RAW_TOKEN)).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> auth.refresh(null)).isInstanceOf(UnauthorizedException.class);
    }

    private RefreshTokenRecord stored(Instant revokedAt, Instant expiresAt) {
        RefreshTokenRecord token = new RefreshTokenRecord(UUID.randomUUID(), tenantId, userId, familyId, expiresAt, revokedAt);
        when(refreshTokens.findByHash(TokenHasher.sha256(RAW_TOKEN))).thenReturn(Optional.of(token));
        return token;
    }
}
