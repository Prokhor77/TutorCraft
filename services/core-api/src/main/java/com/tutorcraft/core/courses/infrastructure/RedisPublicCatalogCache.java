package com.tutorcraft.core.courses.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutorcraft.core.courses.application.PublicCatalogCache;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Кэш витрины в Redis (JSON, TTL по умолчанию 60 с). Инвалидация — O(1): номер поколения tenant входит в ключ,
 * сброс = INCR поколения, старые записи истекают по TTL. Недоступность Redis не ломает витрину (NFR-REL-04).
 */
@Component
class RedisPublicCatalogCache implements PublicCatalogCache {

    private static final Logger log = LoggerFactory.getLogger(RedisPublicCatalogCache.class);
    private static final String PREFIX = "tc:public-catalog:";
    private static final String GENERATION_SUFFIX = ":gen";
    private static final String INITIAL_GENERATION = "0";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Duration ttl;

    RedisPublicCatalogCache(StringRedisTemplate redis, ObjectMapper mapper,
                            @Value("${tutorcraft.courses.public-catalog-ttl:PT60S}") Duration ttl) {
        this.redis = redis;
        this.mapper = mapper;
        this.ttl = ttl;
    }

    @Override
    public <T> Optional<T> get(UUID tenantId, String key, Class<T> type) {
        try {
            String raw = redis.opsForValue().get(entryKey(tenantId, key));
            return raw == null ? Optional.empty() : Optional.of(mapper.readValue(raw, type));
        } catch (DataAccessException e) {
            log.warn("Public catalog cache unavailable on read: {}", e.getClass().getSimpleName());
            return Optional.empty();
        } catch (JsonProcessingException e) {
            log.warn("Public catalog cache entry is unreadable, ignoring: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @Override
    public void put(UUID tenantId, String key, Object value) {
        try {
            redis.opsForValue().set(entryKey(tenantId, key), mapper.writeValueAsString(value), ttl);
        } catch (DataAccessException | JsonProcessingException e) {
            log.warn("Public catalog cache write skipped: {}", e.getClass().getSimpleName());
        }
    }

    @Override
    public void invalidate(UUID tenantId) {
        try {
            redis.opsForValue().increment(generationKey(tenantId));
        } catch (DataAccessException e) {
            log.warn("Public catalog cache invalidation skipped (entries expire by TTL): {}", e.getClass().getSimpleName());
        }
    }

    private String entryKey(UUID tenantId, String key) {
        String generation = redis.opsForValue().get(generationKey(tenantId));
        return PREFIX + tenantId + ":" + (generation == null ? INITIAL_GENERATION : generation) + ":" + key;
    }

    private static String generationKey(UUID tenantId) {
        return PREFIX + tenantId + GENERATION_SUFFIX;
    }
}
