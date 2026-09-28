package com.tutorcraft.core.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.tutorcraft.core.identity.domain.LoginThrottlePolicy.FailureStats;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LoginThrottlePolicyTest {

    private static final Instant NOW = Instant.parse("2026-09-27T18:00:00Z");
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int MAX = 5;

    @Test
    void firstFailuresAreFree() {
        assertThat(LoginThrottlePolicy.accountRetryAfter(null, MAX, WINDOW, NOW)).isEmpty();
        assertThat(LoginThrottlePolicy.accountRetryAfter(new FailureStats(2, NOW), MAX, WINDOW, NOW)).isEmpty();
    }

    @Test
    void delayGrowsExponentiallyAfterFreeAttempts() {
        assertThat(LoginThrottlePolicy.accountRetryAfter(new FailureStats(3, NOW), MAX, WINDOW, NOW)).contains(Duration.ofSeconds(1));
        assertThat(LoginThrottlePolicy.accountRetryAfter(new FailureStats(4, NOW), MAX, WINDOW, NOW)).contains(Duration.ofSeconds(2));
    }

    @Test
    void delayIsCountedFromLastFailure() {
        assertThat(LoginThrottlePolicy.accountRetryAfter(new FailureStats(4, NOW.minusSeconds(2)), MAX, WINDOW, NOW)).isEmpty();
    }

    @Test
    void lockoutUntilWindowEndsAtLimit() {
        assertThat(LoginThrottlePolicy.accountRetryAfter(new FailureStats(MAX, NOW.minusSeconds(60)), MAX, WINDOW, NOW))
                .contains(WINDOW.minusSeconds(60));
    }

    @Test
    void ipIsOnlyLockedOutAtLimit() {
        assertThat(LoginThrottlePolicy.ipRetryAfter(new FailureStats(29, NOW), 30, WINDOW, NOW)).isEmpty();
        assertThat(LoginThrottlePolicy.ipRetryAfter(new FailureStats(30, NOW), 30, WINDOW, NOW)).contains(WINDOW);
    }

    @Test
    void progressiveDelayIsCapped() {
        assertThat(LoginThrottlePolicy.progressiveDelay(40)).isEqualTo(Duration.ofMinutes(1));
    }
}
