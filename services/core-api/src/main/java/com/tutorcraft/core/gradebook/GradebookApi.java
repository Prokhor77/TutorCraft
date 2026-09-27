package com.tutorcraft.core.gradebook;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Публичный API журнала оценок (FR-GRADE). Источники оценок — задания, тесты, форумы. */
public interface GradebookApi {

    /** Создаёт/обновляет элемент оценивания, привязанный к элементу курса. */
    UUID ensureGradeItem(UUID tenantId, UUID courseId, UUID sourceItemId, String name, BigDecimal maxScore, UUID categoryId);

    void removeGradeItem(UUID tenantId, UUID sourceItemId);

    /** Записывает оценку источника. Заблокированные/переопределённые вручную оценки не перезаписываются (FR-GRADE-04). */
    void recordGrade(GradeUpdate update);

    /** Публикует все оценки элемента (FR-ASSIGN-07). @return количество опубликованных */
    int publishAll(UUID tenantId, UUID sourceItemId, UUID actorId);

    Optional<GradeView> grade(UUID tenantId, UUID sourceItemId, UUID userId);

    /** Оценки пользователя по элементам курса (для условий доступа и выполнения). */
    Map<UUID, GradeView> gradesOf(UUID tenantId, UUID userId, Collection<UUID> sourceItemIds);

    Optional<BigDecimal> finalPercent(UUID tenantId, UUID courseId, UUID userId);

    record GradeUpdate(UUID tenantId, UUID courseId, UUID sourceItemId, UUID userId, BigDecimal score,
                       UUID graderId, boolean publish) {
    }

    record GradeView(BigDecimal score, BigDecimal maxScore, boolean published) {

        public Optional<Double> percent() {
            if (score == null || maxScore == null || maxScore.signum() == 0) {
                return Optional.empty();
            }
            return Optional.of(score.doubleValue() * 100.0 / maxScore.doubleValue());
        }
    }
}
