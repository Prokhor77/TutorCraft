package com.tutorcraft.core.gradebook.application;

import com.tutorcraft.core.gradebook.domain.Grade;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Оценки и их история (append-only, DATA-03). */
public interface GradeRepository {

    Optional<Grade> find(UUID tenantId, UUID gradeId);

    /** Строка оценки под блокировкой; создаётся пустой, если её не было. */
    Grade lockOrCreate(UUID tenantId, UUID columnId, UUID userId, Instant now);

    Optional<Grade> lock(UUID tenantId, UUID gradeId);

    List<Grade> ofColumns(UUID tenantId, Collection<UUID> columnIds);

    List<Grade> ofUser(UUID tenantId, UUID userId, Collection<UUID> columnIds);

    /** @return false — версия изменилась */
    boolean update(Grade grade, long expectedVersion, Instant now);

    /** Публикует все неопубликованные оценки столбца с баллом. @return опубликованные */
    List<Grade> publishColumn(UUID tenantId, UUID columnId, Instant now);

    void insertHistory(UUID tenantId, UUID gradeId, BigDecimal oldScore, BigDecimal newScore, UUID actorId, String reason,
                       Instant at);

    List<HistoryRow> history(UUID tenantId, UUID gradeId);

    /** Недавно опубликованные оценки студента по элементам курса (для «Моих задач»). */
    List<PublishedGrade> recentlyPublished(UUID tenantId, UUID userId, int limit);

    record HistoryRow(Instant at, UUID actorId, BigDecimal oldScore, BigDecimal newScore) {
    }

    record PublishedGrade(UUID courseId, UUID sourceItemId, String itemName, BigDecimal score, BigDecimal maxScore,
                          Instant publishedAt) {
    }
}
