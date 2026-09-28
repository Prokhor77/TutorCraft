package com.tutorcraft.core.gradebook.infrastructure;

import com.tutorcraft.core.gradebook.application.GradeRepository;
import com.tutorcraft.core.gradebook.domain.Grade;
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
class JdbcGradeRepository implements GradeRepository {

    private static final String FIELDS = """
            id, tenant_id, grade_item_id, user_id, raw_score, final_score, overridden, locked, published_at, graded_by,
            graded_at, version
            """;

    private final JdbcClient jdbc;

    JdbcGradeRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Grade> find(UUID tenantId, UUID gradeId) {
        return jdbc.sql("SELECT " + FIELDS + " FROM grades WHERE tenant_id = :tenantId AND id = :id")
                .param("tenantId", tenantId).param("id", gradeId)
                .query((rs, n) -> toGrade(rs)).optional();
    }

    @Override
    public Grade lockOrCreate(UUID tenantId, UUID columnId, UUID userId, Instant now) {
        jdbc.sql("""
                INSERT INTO grades (id, tenant_id, grade_item_id, user_id, version, created_at, updated_at)
                VALUES (:id, :tenantId, :columnId, :userId, 0, :now, :now)
                ON CONFLICT (grade_item_id, user_id) DO NOTHING
                """)
            .param("id", Ids.newId()).param("tenantId", tenantId).param("columnId", columnId).param("userId", userId)
            .param("now", Timestamps.of(now))
            .update();
        return jdbc.sql("SELECT " + FIELDS + """
                 FROM grades WHERE tenant_id = :tenantId AND grade_item_id = :columnId AND user_id = :userId FOR UPDATE
                """)
            .param("tenantId", tenantId).param("columnId", columnId).param("userId", userId)
            .query((rs, n) -> toGrade(rs)).single();
    }

    @Override
    public Optional<Grade> lock(UUID tenantId, UUID gradeId) {
        return jdbc.sql("SELECT " + FIELDS + " FROM grades WHERE tenant_id = :tenantId AND id = :id FOR UPDATE")
                .param("tenantId", tenantId).param("id", gradeId)
                .query((rs, n) -> toGrade(rs)).optional();
    }

    @Override
    public List<Grade> ofColumns(UUID tenantId, Collection<UUID> columnIds) {
        if (columnIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("SELECT " + FIELDS + " FROM grades WHERE tenant_id = :tenantId AND grade_item_id IN (:ids)")
                .param("tenantId", tenantId).param("ids", List.copyOf(columnIds))
                .query((rs, n) -> toGrade(rs)).list();
    }

    @Override
    public List<Grade> ofUser(UUID tenantId, UUID userId, Collection<UUID> columnIds) {
        if (columnIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("SELECT " + FIELDS + """
                 FROM grades WHERE tenant_id = :tenantId AND user_id = :userId AND grade_item_id IN (:ids)
                """)
            .param("tenantId", tenantId).param("userId", userId).param("ids", List.copyOf(columnIds))
            .query((rs, n) -> toGrade(rs)).list();
    }

    @Override
    public boolean update(Grade grade, long expectedVersion, Instant now) {
        return jdbc.sql("""
                UPDATE grades SET raw_score = :raw, final_score = :final, overridden = :overridden, locked = :locked,
                       published_at = :publishedAt, graded_by = :gradedBy, graded_at = :gradedAt,
                       version = version + 1, updated_at = :now
                WHERE tenant_id = :tenantId AND id = :id AND version = :expected
                """)
            .param("raw", grade.rawScore()).param("final", grade.finalScore()).param("overridden", grade.overridden())
            .param("locked", grade.locked()).param("publishedAt", Timestamps.of(grade.publishedAt()))
            .param("gradedBy", grade.gradedBy()).param("gradedAt", Timestamps.of(grade.gradedAt()))
            .param("now", Timestamps.of(now)).param("tenantId", grade.tenantId()).param("id", grade.id())
            .param("expected", expectedVersion)
            .update() == 1;
    }

    @Override
    public List<Grade> publishColumn(UUID tenantId, UUID columnId, Instant now) {
        return jdbc.sql("""
                UPDATE grades SET published_at = :now, version = version + 1, updated_at = :now
                WHERE tenant_id = :tenantId AND grade_item_id = :columnId AND published_at IS NULL AND final_score IS NOT NULL
                RETURNING\s""" + FIELDS)
            .param("now", Timestamps.of(now)).param("tenantId", tenantId).param("columnId", columnId)
            .query((rs, n) -> toGrade(rs)).list();
    }

    @Override
    public void insertHistory(UUID tenantId, UUID gradeId, BigDecimal oldScore, BigDecimal newScore, UUID actorId,
                              String reason, Instant at) {
        jdbc.sql("""
                INSERT INTO grade_history (id, tenant_id, grade_id, old_score, new_score, actor_id, reason, at)
                VALUES (:id, :tenantId, :gradeId, :oldScore, :newScore, :actorId, :reason, :at)
                """)
            .param("id", Ids.newId()).param("tenantId", tenantId).param("gradeId", gradeId)
            .param("oldScore", oldScore).param("newScore", newScore).param("actorId", actorId).param("reason", reason)
            .param("at", Timestamps.of(at))
            .update();
    }

    @Override
    public List<HistoryRow> history(UUID tenantId, UUID gradeId) {
        return jdbc.sql("""
                SELECT at, actor_id, old_score, new_score FROM grade_history
                WHERE tenant_id = :tenantId AND grade_id = :gradeId ORDER BY at DESC, id DESC
                """)
            .param("tenantId", tenantId).param("gradeId", gradeId)
            .query((rs, n) -> new HistoryRow(Timestamps.read(rs, "at"), rs.getObject("actor_id", UUID.class),
                    rs.getBigDecimal("old_score"), rs.getBigDecimal("new_score")))
            .list();
    }

    @Override
    public List<PublishedGrade> recentlyPublished(UUID tenantId, UUID userId, int limit) {
        return jdbc.sql("""
                SELECT gi.course_id, gi.source_item_id, gi.name, g.final_score, gi.max_score, g.published_at
                FROM grades g JOIN grade_items gi ON gi.id = g.grade_item_id
                WHERE g.tenant_id = :tenantId AND g.user_id = :userId AND g.published_at IS NOT NULL
                  AND g.final_score IS NOT NULL AND gi.deleted_at IS NULL AND gi.source_item_id IS NOT NULL
                ORDER BY g.published_at DESC LIMIT :limit
                """)
            .param("tenantId", tenantId).param("userId", userId).param("limit", limit)
            .query((rs, n) -> new PublishedGrade(rs.getObject("course_id", UUID.class),
                    rs.getObject("source_item_id", UUID.class), rs.getString("name"), rs.getBigDecimal("final_score"),
                    rs.getBigDecimal("max_score"), Timestamps.read(rs, "published_at")))
            .list();
    }

    private static Grade toGrade(ResultSet rs) throws SQLException {
        return new Grade(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getObject("grade_item_id", UUID.class), rs.getObject("user_id", UUID.class),
                rs.getBigDecimal("raw_score"), rs.getBigDecimal("final_score"), rs.getBoolean("overridden"),
                rs.getBoolean("locked"), Timestamps.read(rs, "published_at"), rs.getObject("graded_by", UUID.class),
                Timestamps.read(rs, "graded_at"), rs.getLong("version"));
    }
}
