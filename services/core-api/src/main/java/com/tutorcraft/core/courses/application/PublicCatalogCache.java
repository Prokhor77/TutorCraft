package com.tutorcraft.core.courses.application;

import java.util.Optional;
import java.util.UUID;

/**
 * Кэш публичной витрины (FR-COURSE-HYB-01). Реализация обязана деградировать без ошибок при недоступности хранилища:
 * get → пусто, put/invalidate → no-op (NFR-REL-04).
 */
public interface PublicCatalogCache {

    <T> Optional<T> get(UUID tenantId, String key, Class<T> type);

    void put(UUID tenantId, String key, Object value);

    /** Сбрасывает все записи витрины tenant. */
    void invalidate(UUID tenantId);
}
