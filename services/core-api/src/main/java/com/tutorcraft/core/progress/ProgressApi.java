package com.tutorcraft.core.progress;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Публичный API модуля progress. */
public interface ProgressApi {

    /** Процент выполнения курса пользователем (0..100) по элементам с отслеживанием выполнения. */
    Map<UUID, Integer> completionPercents(UUID tenantId, UUID userId, Collection<UUID> courseIds);

    Optional<Instant> courseCompletedAt(UUID tenantId, UUID courseId, UUID userId);

    /** Последний открытый элемент в курсе («Продолжить обучение», FR-DASH-01). */
    Optional<UUID> lastViewedItem(UUID tenantId, UUID courseId, UUID userId);
}
