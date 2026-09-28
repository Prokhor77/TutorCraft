package com.tutorcraft.core.enrollment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tutorcraft.core.access.domain.CourseRole;
import com.tutorcraft.core.enrollment.domain.InviteLink.Validity;
import com.tutorcraft.core.shared.domain.ValidationException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** FR-ENROL-03: срок действия, лимит использований, отзыв. */
class InviteLinkTest {

    private static final Instant NOW = Instant.parse("2026-09-27T10:00:00Z");

    private static InviteLink link(Instant expiresAt, Integer maxUses, int uses, Instant revokedAt) {
        return new InviteLink(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "hash", CourseRole.STUDENT, expiresAt,
                maxUses, uses, revokedAt, UUID.randomUUID(), NOW.minus(Duration.ofDays(1)));
    }

    @Test
    void unlimitedLinkIsValid() {
        assertThat(link(null, null, 1000, null).validityAt(NOW)).isEqualTo(Validity.VALID);
    }

    @Test
    void expiresAtExpiryInstant() {
        InviteLink link = link(NOW.plusSeconds(1), null, 0, null);

        assertThat(link.usableAt(NOW)).isTrue();
        assertThat(link.validityAt(NOW.plusSeconds(1))).isEqualTo(Validity.EXPIRED);
    }

    @Test
    void exhaustedWhenUsesReachLimit() {
        assertThat(link(null, 3, 2, null).usableAt(NOW)).isTrue();
        assertThat(link(null, 3, 3, null).validityAt(NOW)).isEqualTo(Validity.EXHAUSTED);
    }

    @Test
    void revocationWinsOverEverything() {
        assertThat(link(NOW.minusSeconds(5), 1, 1, NOW.minusSeconds(10)).validityAt(NOW)).isEqualTo(Validity.REVOKED);
    }

    @Test
    void newLinkParametersAreValidated() {
        assertThatCode(() -> InviteLink.validateNew(null, null, NOW)).doesNotThrowAnyException();
        assertThatCode(() -> InviteLink.validateNew(NOW.plusSeconds(60), 10, NOW)).doesNotThrowAnyException();
        assertThatThrownBy(() -> InviteLink.validateNew(NOW, null, NOW)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> InviteLink.validateNew(null, 0, NOW)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> InviteLink.validateNew(null, InviteLink.MAX_USES_LIMIT + 1, NOW))
                .isInstanceOf(ValidationException.class);
    }
}
