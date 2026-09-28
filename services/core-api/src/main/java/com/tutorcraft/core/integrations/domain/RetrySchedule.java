package com.tutorcraft.core.integrations.domain;

import java.time.Duration;
import java.util.Optional;

/** Повторы доставки вебхука: 30 с × 2^(n−1), не более {@link #MAX_ATTEMPTS} попыток. */
public final class RetrySchedule {

    public static final int MAX_ATTEMPTS = 8;
    private static final Duration BASE_DELAY = Duration.ofSeconds(30);

    private RetrySchedule() {
    }

    /**
     * @param attemptsMade сколько попыток уже сделано (≥ 1)
     * @return задержка до следующей попытки; пусто — попытки исчерпаны
     */
    public static Optional<Duration> delayAfter(int attemptsMade) {
        if (attemptsMade < 1) {
            throw new IllegalArgumentException("attemptsMade must be positive");
        }
        if (attemptsMade >= MAX_ATTEMPTS) {
            return Optional.empty();
        }
        return Optional.of(BASE_DELAY.multipliedBy(1L << (attemptsMade - 1)));
    }
}
