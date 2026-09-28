package com.tutorcraft.core.identity.application;

import com.tutorcraft.core.shared.domain.RateLimitedException;
import java.time.Duration;
import java.util.Map;

/** Вход временно заблокирован (429 + Retry-After). */
public class LoginThrottledException extends RateLimitedException {

    private static final String RETRY_AFTER_ARG = "retryAfterSeconds";

    private final long retryAfterSeconds;

    public LoginThrottledException(Duration retryAfter) {
        this(Math.max(1, (retryAfter.toMillis() + 999) / 1000));
    }

    private LoginThrottledException(long seconds) {
        super(IdentityErrors.TOO_MANY_ATTEMPTS, "Too many login attempts", Map.of(RETRY_AFTER_ARG, seconds));
        this.retryAfterSeconds = seconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
