package com.tutorcraft.core.gradebook.infrastructure;

import com.tutorcraft.core.gradebook.application.GradebookStructureRepository;
import com.tutorcraft.core.gradebook.domain.Aggregation;
import com.tutorcraft.core.gradebook.domain.GradeCategory;
import com.tutorcraft.core.gradebook.domain.GradeColumn;
import com.tutorcraft.core.shared.domain.Ids;
import com.tutorcraft.core.shared.persistence.Timestamps;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcGradebookStructureRepository implements GradebookStructureRepository {

    private static final String COLUMN_FIELDS = "id, course_id, source_item_id, name, max_score, category_id, position";
    /** Несуществующий UUID для пустого списка в NOT IN (...). */
    private static final UUID NONE = new UUID(0L, 0L);

    private final JdbcClient jdbc;

    JdbcGradebookStructureRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<GradebookSettings> settings(UUID tenantId, UUID courseId) {
        return jdbc.sql("""
                SELECT course_id, aggregation, scale_id, version FROM gradebook_settings
                WHERE tenant_id = :tenantId AND course_id = :courseId
                """)
            .param("tenantId", tenantId).param("courseId", courseId)
            .query((rs, n) -> new GradebookSettings(rs.getObject("course_id", UUID.class),
                    Aggregation.find(rs.getString("aggregation")).orElse(Aggregation.WEIGHTED_MEAN),
                    rs.getObject("scale_id", UUID.class), rs.getLong("version")))
            .optional();
    }

    @Override
    public void saveSettings(UUID tenantId, UUID courseId, Aggregation aggregation, UUID scaleId, Instant now) {
        jdbc.sql("""
                INSERT INTO gradebook_settings (course_id, tenant_id, aggregation, scale_id, version, updated_at)
                VALUES (:courseId, :tenantId, :aggregation, :scaleId, 0, :now)
                ON CONFLICT (course_id) DO UPDATE SET aggregation = EXCLUDED.aggregation, scale_id = EXCLUDED.scale_id,
                    version = gradebook_settings.version + 1, updated_at = EXCLUDED.updated_at
                WHERE gradebook_settings.tenant_id = EXCLUDED.tenant_id
                """)
            .param("courseId", courseId).param("tenantId", tenantId).param("aggregation", aggregation.key())
            .param("scaleId", scaleId).param("now", Timestamps.of(now))
            .update();
    }

    @Override
    public List<GradeCategory> categories(UUID tenantId, UUID courseId) {
        return jdbc.sql("""
                SELECT id, name, weight, position FROM grade_categories
                WHERE tenant_id = :tenantId AND course_id = :courseId ORDER BY position, created_at
                """)
            .param("tenantId", tenantId).param("courseId", courseId)
            .query((rs, n) -> new GradeCategory(rs.getObject("id", UUID.class), rs.getString("name"),
                    rs.getBigDecimal("weight"), rs.getInt("position")))
            .list();
    }

    @Override
    public void insertCategory(UUID tenantId, UUID courseId, GradeCategory category, Instant now) {
        jdbc.sql("""
                INSERT INTO grade_categories (id, tenant_id, course_id, name, weight, position, created_at)
                VALUES (:id, :tenantId, :courseId, :name, :weight, :position, :now)
                """)
            .param("id", category.id()).param("tenantId", tenantId).param("courseId", courseId)
            .param("name", category.name()).param("weight", category.weight()).param("position", category.position())
            .param("now", Timestamps.of(now))
            .update();
    }

    @Override
    public void updateCategory(UUID tenantId, UUID courseId, GradeCategory category) {
        jdbc.sql("""
                UPDATE grade_categories SET name = :name, weight = :weight, position = :position
                WHERE tenant_id = :tenantId AND course_id = :courseId AND id = :id
                """)
            .param("name", category.name()).param("weight", category.weight()).param("position", category.position())
            .param("tenantId", tenantId).param("courseId", courseId).param("id", category.id())
            .update();
    }

    @Override
    public void deleteCategoriesExcept(UUID tenantId, UUID courseId, Collection<UUID> keepIds) {
        List<UUID> keep = keepIds.isEmpty() ? List.of(NONE) : List.copyOf(keepIds);
        jdbc.sql("DELETE FROM grade_categories WHERE tenant_id = :tenantId AND course_id = :courseId AND id NOT IN (:keep)")
            .param("tenantId", tenantId).param("courseId", courseId).param("keep", keep)
            .update();
    }

    @Override
    public List<GradeColumn> columns(UUID tenantId, UUID courseId) {
        return jdbc.sql("SELECT " + COLUMN_FIELDS + """
                 FROM grade_items WHERE tenant_id = :tenantId AND course_id = :courseId AND deleted_at IS NULL
                ORDER BY position, created_at
                """)
            .param("tenantId", tenantId).param("courseId", courseId)
            .query((rs, n) -> toColumn(rs)).list();
    }

    @Override
    public Optional<GradeColumn> column(UUID tenantId, UUID columnId) {
        return jdbc.sql("SELECT " + COLUMN_FIELDS + " FROM grade_items WHERE tenant_id = :tenantId AND id = :id AND deleted_at IS NULL")
                .param("tenantId", tenantId).param("id", columnId)
                .query((rs, n) -> toColumn(rs)).optional();
    }

    @Override
    public Optional<GradeColumn> columnBySource(UUID tenantId, UUID sourceItemId) {
        return jdbc.sql("SELECT " + COLUMN_FIELDS + """
                 FROM grade_items WHERE tenant_id = :tenantId AND source_item_id = :sourceItemId AND deleted_at IS NULL
                """)
            .param("tenantId", tenantId).param("sourceItemId", sourceItemId)
            .query((rs, n) -> toColumn(rs)).optional();
    }

    @Override
    public List<GradeColumn> columnsBySource(UUID tenantId, Collection<UUID> sourceItemIds) {
        if (sourceItemIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("SELECT " + COLUMN_FIELDS + """
                 FROM grade_items WHERE tenant_id = :tenantId AND source_item_id IN (:ids) AND deleted_at IS NULL
                """)
            .param("tenantId", tenantId).param("ids", List.copyOf(sourceItemIds))
            .query((rs, n) -> toColumn(rs)).list();
    }

    @Override
    public UUID upsertSourceColumn(UUID tenantId, UUID courseId, UUID sourceItemId, String name, BigDecimal maxScore,
                                   UUID categoryId, Instant now) {
        return jdbc.sql("""
                INSERT INTO grade_items (id, tenant_id, course_id, source_item_id, name, max_score, category_id, position,
                                         created_at, updated_at)
                VALUES (:id, :tenantId, :courseId, :sourceItemId, :name, :maxScore, :categoryId,
                        (SELECT COALESCE(MAX(position) + 1, 0) FROM grade_items WHERE tenant_id = :tenantId AND course_id = :courseId),
                        :now, :now)
                ON CONFLICT (tenant_id, source_item_id) WHERE source_item_id IS NOT NULL DO UPDATE
                SET name = EXCLUDED.name, max_score = EXCLUDED.max_score,
                    category_id = COALESCE(EXCLUDED.category_id, grade_items.category_id),
                    deleted_at = NULL, updated_at = EXCLUDED.updated_at
                RETURNING id
                """)
            .param("id", Ids.newId()).param("tenantId", tenantId).param("courseId", courseId)
            .param("sourceItemId", sourceItemId).param("name", name).param("maxScore", maxScore)
            .param("categoryId", categoryId).param("now", Timestamps.of(now))
            .query((rs, n) -> rs.getObject("id", UUID.class)).single();
    }

    @Override
    public void insertColumn(UUID tenantId, GradeColumn column, Instant now) {
        jdbc.sql("""
                INSERT INTO grade_items (id, tenant_id, course_id, source_item_id, name, max_score, category_id, position,
                                         created_at, updated_at)
                VALUES (:id, :tenantId, :courseId, NULL, :name, :maxScore, :categoryId,
                        (SELECT COALESCE(MAX(position) + 1, 0) FROM grade_items WHERE tenant_id = :tenantId AND course_id = :courseId),
                        :now, :now)
                """)
            .param("id", column.id()).param("tenantId", tenantId).param("courseId", column.courseId())
            .param("name", column.name()).param("maxScore", column.maxScore()).param("categoryId", column.categoryId())
            .param("now", Timestamps.of(now))
            .update();
    }

    @Override
    public void softDeleteBySource(UUID tenantId, UUID sourceItemId, Instant now) {
        jdbc.sql("""
                UPDATE grade_items SET deleted_at = :now, updated_at = :now
                WHERE tenant_id = :tenantId AND source_item_id = :sourceItemId AND deleted_at IS NULL
                """)
            .param("now", Timestamps.of(now)).param("tenantId", tenantId).param("sourceItemId", sourceItemId)
            .update();
    }

    @Override
    public void assignCategory(UUID tenantId, UUID courseId, UUID columnId, UUID categoryId) {
        jdbc.sql("""
                UPDATE grade_items SET category_id = :categoryId
                WHERE tenant_id = :tenantId AND course_id = :courseId AND id = :id
                """)
            .param("categoryId", categoryId).param("tenantId", tenantId).param("courseId", courseId).param("id", columnId)
            .update();
    }

    private static GradeColumn toColumn(ResultSet rs) throws SQLException {
        return new GradeColumn(rs.getObject("id", UUID.class), rs.getObject("course_id", UUID.class),
                rs.getObject("source_item_id", UUID.class), rs.getString("name"), rs.getBigDecimal("max_score"),
                rs.getObject("category_id", UUID.class), rs.getInt("position"));
    }
}
