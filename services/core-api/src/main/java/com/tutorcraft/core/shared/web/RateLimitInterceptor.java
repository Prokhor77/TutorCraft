package com.tutorcraft.core.shared.web;

import com.tutorcraft.core.shared.config.AppProperties;
import com.tutorcraft.core.shared.domain.RateLimitedException;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Rate limiting фиксированным окном в Redis (API-07). Ключ — пользователь, иначе IP.
 * При недоступности Redis запросы пропускаются (graceful degradation, NFR-REL-04).
 */
@Component
class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final String KEY_PREFIX = "rl:";

    private final StringRedisTemplate redis;
    private final CurrentUserProvider currentUser;
    private final AppProperties.RateLimit limits;
    private final Clock clock;

    RateLimitInterceptor(StringRedisTemplate redis, CurrentUserProvider currentUser, AppProperties properties, Clock clock) {
        this.redis = redis;
        this.currentUser = currentUser;
        this.limits = properties.rateLimit();
        this.clock = clock;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Optional<CurrentUser> user = currentUser.find();
        int limit = user.isPresent() ? limits.requestsPerMinuteUser() : limits.requestsPerMinuteIp();
        String subject = user.map(u -> "u:" + u.userId()).orElseGet(() -> "ip:" + ClientIp.of(request));
        long windowStart = clock.instant().getEpochSecond() / WINDOW.toSeconds();
        Long count = increment(KEY_PREFIX + subject + ":" + windowStart);
        if (count == null) {
            return true;
        }
        long resetSeconds = WINDOW.toSeconds() - clock.instant().getEpochSecond() % WINDOW.toSeconds();
        response.setHeader("RateLimit-Limit", String.valueOf(limit));
        response.setHeader("RateLimit-Remaining", String.valueOf(Math.max(0, limit - count)));
        response.setHeader("RateLimit-Reset", String.valueOf(resetSeconds));
        if (count > limit) {
            throw new RateLimitedException("rate_limit.exceeded", "Too many requests");
        }
        return true;
    }

    private Long increment(String key) {
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, WINDOW);
            }
            return count;
        } catch (RedisConnectionFailureException e) {
            log.warn("Rate limiter unavailable, request allowed");
            return null;
        }
    }
}
