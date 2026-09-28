package com.tutorcraft.core.identity.infrastructure;

import com.tutorcraft.core.identity.application.LoginThrottle;
import com.tutorcraft.core.identity.domain.LoginThrottlePolicy;
import com.tutorcraft.core.identity.domain.LoginThrottlePolicy.FailureStats;
import com.tutorcraft.core.shared.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Счётчики неудачных входов в Redis: hash {failures, last} с TTL окна (скользящее окно от последней неудачи).
 * При недоступности Redis вход не блокируется (NFR-REL-04), событие логируется.
 */
@Component
class RedisLoginThrottle implements LoginThrottle {

    private static final Logger log = LoggerFactory.getLogger(RedisLoginThrottle.class);
    private static final String ACCOUNT_PREFIX = "login:acct:";
    private static final String IP_PREFIX = "login:ip:";
    private static final String FAILURES = "failures";
    private static final String LAST_FAILURE = "last";

    private final StringRedisTemplate redis;
    private final Clock clock;
    private final AppProperties.Security limits;

    RedisLoginThrottle(StringRedisTemplate redis, Clock clock, AppProperties properties) {
        this.redis = redis;
        this.clock = clock;
        this.limits = properties.security();
    }

    @Override
    public Optional<Duration> retryAfter(String accountKey, String ip) {
        try {
            Instant now = clock.instant();
            Optional<Duration> account = LoginThrottlePolicy.accountRetryAfter(stats(ACCOUNT_PREFIX + accountKey),
                    limits.loginMaxAttemptsPerAccount(), limits.loginWindow(), now);
            Optional<Duration> byIp = LoginThrottlePolicy.ipRetryAfter(stats(IP_PREFIX + ip),
                    limits.loginMaxAttemptsPerIp(), limits.loginWindow(), now);
            return Stream.of(account, byIp).flatMap(Optional::stream).max(Duration::compareTo);
        } catch (DataAccessException e) {
            log.warn("Login throttle unavailable, attempt allowed: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @Override
    public void recordFailure(String accountKey, String ip) {
        try {
            increment(ACCOUNT_PREFIX + accountKey);
            increment(IP_PREFIX + ip);
        } catch (DataAccessException e) {
            log.warn("Login throttle unavailable, failure not recorded: {}", e.getClass().getSimpleName());
        }
    }

    @Override
    public void reset(String accountKey) {
        try {
            redis.delete(ACCOUNT_PREFIX + accountKey);
        } catch (DataAccessException e) {
            log.warn("Login throttle unavailable, reset skipped: {}", e.getClass().getSimpleName());
        }
    }

    private void increment(String key) {
        redis.opsForHash().increment(key, FAILURES, 1);
        redis.opsForHash().put(key, LAST_FAILURE, String.valueOf(clock.millis()));
        redis.expire(key, limits.loginWindow());
    }

    private FailureStats stats(String key) {
        Map<Object, Object> entries = redis.opsForHash().entries(key);
        Object failures = entries.get(FAILURES);
        Object last = entries.get(LAST_FAILURE);
        if (failures == null || last == null) {
            return null;
        }
        try {
            return new FailureStats(Integer.parseInt(failures.toString()), Instant.ofEpochMilli(Long.parseLong(last.toString())));
        } catch (NumberFormatException e) {
            log.warn("Corrupted login throttle entry, ignoring");
            return null;
        }
    }
}
