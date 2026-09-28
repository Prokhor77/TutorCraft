package com.tutorcraft.core.gradebook.application;

import com.tutorcraft.core.gradebook.domain.Aggregation;
import com.tutorcraft.core.gradebook.domain.GradeCategory;
import com.tutorcraft.core.gradebook.domain.GradeColumn;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Структура журнала курса: настройки, категории, столбцы. Все методы фильтруют по tenant. */
public interface GradebookStructureRepository {

    Optional<GradebookSettings> settings(UUID tenantId, UUID courseId);

    void saveSettings(UUID tenantId, UUID courseId, Aggregation aggregation, UUID scaleId, Instant now);

    List<GradeCategory> categories(UUID tenantId, UUID courseId);

    void insertCategory(UUID tenantId, UUID courseId, GradeCategory category, Instant now);

    void updateCategory(UUID tenantId, UUID courseId, GradeCategory category);

    void deleteCategoriesExcept(UUID tenantId, UUID courseId, Collection<UUID> keepIds);

    /** Активные (не удалённые) столбцы курса по позиции. */
    List<GradeColumn> columns(UUID tenantId, UUID courseId);

    Optional<GradeColumn> column(UUID tenantId, UUID columnId);

    Optional<GradeColumn> columnBySource(UUID tenantId, UUID sourceItemId);

    List<GradeColumn> columnsBySource(UUID tenantId, Collection<UUID> sourceItemIds);

    /** Создаёт или обновляет (и восстанавливает) столбец элемента курса. categoryId == null — категория не меняется. */
    UUID upsertSourceColumn(UUID tenantId, UUID courseId, UUID sourceItemId, String name, BigDecimal maxScore,
                            UUID categoryId, Instant now);

    void insertColumn(UUID tenantId, GradeColumn column, Instant now);

    void softDeleteBySource(UUID tenantId, UUID sourceItemId, Instant now);

    void assignCategory(UUID tenantId, UUID courseId, UUID columnId, UUID categoryId);

    record GradebookSettings(UUID courseId, Aggregation aggregation, UUID scaleId, long version) {

        public static GradebookSettings defaults(UUID courseId) {
            return new GradebookSettings(courseId, Aggregation.WEIGHTED_MEAN, null, 0);
        }
    }
}
