package com.tutorcraft.core.billing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tutorcraft.core.shared.domain.Money;
import com.tutorcraft.core.shared.domain.ValidationException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TenantSubscriptionTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-7000-8000-000000000001");
    private static final Instant NOW = Instant.parse("2026-09-28T10:00:00Z");
    private static final Instant TRIAL_END = Instant.parse("2026-10-12T10:00:00Z");

    @Test
    void newSubscriptionStartsFourteenDayTrial() {
        TenantSubscription trial = TenantSubscription.startTrial(TENANT, NOW);

        assertThat(trial.trialEndsAt()).isEqualTo(TRIAL_END);
        assertThat(trial.paidUntil()).isNull();
        assertThat(trial.accessUntil()).isEqualTo(TRIAL_END);
        assertThat(trial.statusAt(NOW)).isEqualTo(SubscriptionStatus.TRIAL);
        assertThat(trial.statusAt(TRIAL_END)).isEqualTo(SubscriptionStatus.EXPIRED);
    }

    @Test
    void paidPeriodMakesSubscriptionActiveUntilItsEnd() {
        Instant paidUntil = Instant.parse("2027-01-12T10:00:00Z");
        TenantSubscription paid = new TenantSubscription(TENANT, TRIAL_END, paidUntil, 1);

        assertThat(paid.statusAt(NOW)).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(paid.statusAt(paidUntil.minusSeconds(1))).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(paid.statusAt(paidUntil)).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(paid.accessUntil()).isEqualTo(paidUntil);
    }

    @Test
    void purchaseDuringTrialStartsWhenTrialEnds() {
        TenantSubscription trial = TenantSubscription.startTrial(TENANT, NOW);

        Instant start = trial.nextPeriodStart(NOW);

        assertThat(start).isEqualTo(TRIAL_END);
        assertThat(TenantSubscription.periodEnd(start, SubscriptionTerm.QUARTER))
            .isEqualTo(Instant.parse("2027-01-12T10:00:00Z"));
    }

    @Test
    void purchaseAfterExpiryStartsNow() {
        TenantSubscription expired = new TenantSubscription(TENANT, TRIAL_END, null, 0);
        Instant later = Instant.parse("2026-11-01T00:00:00Z");

        assertThat(expired.nextPeriodStart(later)).isEqualTo(later);
        assertThat(TenantSubscription.periodEnd(later, SubscriptionTerm.YEAR))
            .isEqualTo(Instant.parse("2027-11-01T00:00:00Z"));
    }

    @ParameterizedTest
    @CsvSource({"month, 1, 4000", "quarter, 3, 12000", "year, 12, 24000"})
    void termsHaveFixedDurationAndUsdPrice(String key, int months, long priceMinor) {
        SubscriptionTerm term = SubscriptionTerm.fromKey(key);

        assertThat(term.months()).isEqualTo(months);
        assertThat(term.price()).isEqualTo(new Money(priceMinor, "USD"));
    }

    @Test
    void unknownTermIsRejected() {
        assertThatThrownBy(() -> SubscriptionTerm.fromKey("week")).isInstanceOf(ValidationException.class);
    }
}
