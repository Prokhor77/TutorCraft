package com.tutorcraft.core.identity.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Троттлинг входа (FR-AUTH-03).
 * <ul>
 *   <li>По аккаунту — прогрессивная задержка: после {@link #FREE_ATTEMPTS} неудач следующая попытка разрешена не раньше
 *       BASE_DELAY × 2^(n − FREE_ATTEMPTS) (не более MAX_DELAY) после последней неудачи; на лимите — блокировка до конца окна.</li>
 *   <li>По IP — только блокировка на лимите: за одним NAT (школа, офис) могут ошибаться разные люди,
 *       и прогрессивная задержка по IP замедляла бы всех.</li>
 * </ul>
 */
public final class LoginThrottlePolicy {

    public static final int FREE_ATTEMPTS = 3;
    private static final Duration BASE_DELAY = Duration.ofSeconds(1);
    private static final Duration MAX_DELAY = Duration.ofMinutes(1);
    private static final int MAX_SHIFT = 16;

    private LoginThrottlePolicy() {
    }

    /** @return сколько ждать до следующей попытки входа в аккаунт; пусто — попытка разрешена */
    public static Optional<Duration> accountRetryAfter(FailureStats stats, int maxAttempts, Duration window, Instant now) {
        if (stats == null || stats.failures() < FREE_ATTEMPTS) {
            return Optional.empty();
        }
        Duration wait = stats.failures() >= maxAttempts ? window : progressiveDelay(stats.failures());
        return remaining(stats, wait, now);
    }

    /** @return сколько ждать до следующей попытки с этого IP; пусто — попытка разрешена */
    public static Optional<Duration> ipRetryAfter(FailureStats stats, int maxAttempts, Duration window, Instant now) {
        if (stats == null || stats.failures() < maxAttempts) {
            return Optional.empty();
        }
        return remaining(stats, window, now);
    }

    static Duration progressiveDelay(int failures) {
        int shift = Math.min(failures - FREE_ATTEMPTS, MAX_SHIFT);
        Duration delay = BASE_DELAY.multipliedBy(1L << shift);
        return delay.compareTo(MAX_DELAY) > 0 ? MAX_DELAY : delay;
    }

    private static Optional<Duration> remaining(FailureStats stats, Duration wait, Instant now) {
        Duration remaining = Duration.between(now, stats.lastFailureAt().plus(wait));
        return remaining.isNegative() || remaining.isZero() ? Optional.empty() : Optional.of(remaining);
    }

    public record FailureStats(int failures, Instant lastFailureAt) {
    }
}
