package com.tutorcraft.core.gradebook.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.tutorcraft.core.gradebook.application.ScaleRepository;
import com.tutorcraft.core.gradebook.domain.ScaleLevel;
import com.tutorcraft.core.shared.persistence.JsonCodec;
import com.tutorcraft.core.shared.persistence.Timestamps;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcScaleRepository implements ScaleRepository {

    private static final TypeReference<List<ScaleLevel>> LEVELS = new TypeReference<>() { };
    private static final String FIELDS = "id, tenant_id, course_id, name, levels::text AS levels";

    private final JdbcClient jdbc;
    private final JsonCodec json;

    JdbcScaleRepository(JdbcClient jdbc, JsonCodec json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public List<Scale> list(UUID tenantId, UUID courseId) {
        return jdbc.sql("SELECT " + FIELDS + """
                 FROM scales WHERE tenant_id = :tenantId
                  AND (course_id IS NULL OR course_id = CAST(:courseId AS uuid))
                ORDER BY course_id NULLS FIRST, created_at
                """)
            .param("tenantId", tenantId).param("courseId", courseId)
            .query((rs, n) -> toScale(rs)).list();
    }

    @Override
    public Optional<Scale> find(UUID tenantId, UUID scaleId) {
        return jdbc.sql("SELECT " + FIELDS + " FROM scales WHERE tenant_id = :tenantId AND id = :id")
                .param("tenantId", tenantId).param("id", scaleId)
                .query((rs, n) -> toScale(rs)).optional();
    }

    @Override
    public boolean hasTenantScales(UUID tenantId) {
        return jdbc.sql("SELECT COUNT(*) FROM scales WHERE tenant_id = :tenantId AND course_id IS NULL")
                .param("tenantId", tenantId)
                .query(Long.class).single() > 0;
    }

    @Override
    public void insert(Scale scale, Instant now) {
        jdbc.sql("""
                INSERT INTO scales (id, tenant_id, course_id, name, levels, created_at)
                VALUES (:id, :tenantId, :courseId, :name, :levels, :now)
                ON CONFLICT DO NOTHING
                """)
            .param("id", scale.id()).param("tenantId", scale.tenantId()).param("courseId", scale.courseId())
            .param("name", scale.name()).param("levels", json.toJsonb(scale.levels())).param("now", Timestamps.of(now))
            .update();
    }

    private Scale toScale(ResultSet rs) throws SQLException {
        return new Scale(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getObject("course_id", UUID.class), rs.getString("name"), json.read(rs.getString("levels"), LEVELS));
    }
}
