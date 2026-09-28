package com.tutorcraft.core.gradebook.spi;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Источник опубликованных отзывов для «Моих оценок» (FR-GRADE-08). Реализует модуль-владелец оценки
 * (задания — assessment.assignment). Реализация зависит только от своих репозиториев.
 */
public interface GradeFeedbackSource {

    /** Отзывы, видимые студенту: sourceItemId → BlockDoc. Элементы без опубликованного отзыва отсутствуют. */
    Map<UUID, Map<String, Object>> publishedFeedback(UUID tenantId, UUID userId, Collection<UUID> sourceItemIds);
}
