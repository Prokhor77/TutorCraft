package com.tutorcraft.core.identity.application;

import java.time.Duration;
import java.util.Optional;

/**
 * Троттлинг попыток входа по аккаунту и IP (FR-AUTH-03). accountKey — хеш email, не сам email (NFR-SEC-11).
 * Реализация деградирует мягко: при недоступности хранилища попытки разрешаются.
 */
public interface LoginThrottle {

    Optional<Duration> retryAfter(String accountKey, String ip);

    void recordFailure(String accountKey, String ip);

    void reset(String accountKey);
}
