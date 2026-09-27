package com.tutorcraft.core.access.spi;

import java.util.Optional;
import java.util.UUID;

/** Порт: курс существует в tenant (не удалён) и его категория. Реализует модуль courses. */
public interface CourseLocator {

    /** Пусто — курса нет в этом tenant (→ 404, AC-1). Внутренний Optional — категория курса. */
    Optional<Optional<UUID>> categoryOf(UUID tenantId, UUID courseId);

    /** Курс существует, но в другом tenant — для аудита попыток обхода изоляции (AC-1). */
    boolean existsInOtherTenant(UUID tenantId, UUID courseId);
}
