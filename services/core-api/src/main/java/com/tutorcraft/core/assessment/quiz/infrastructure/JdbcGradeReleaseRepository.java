package com.tutorcraft.core.assessment.quiz.infrastructure;

import com.tutorcraft.core.assessment.quiz.application.GradeReleaseRepository;
import com.tutorcraft.core.shared.persistence.Timestamps;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcGradeReleaseRepository implements GradeReleaseRepository {

    private final JdbcClient jdbc;

    JdbcGradeReleaseRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Новая дата закрытия снова включает публикацию (тест переоткрыт). */
    @Override
    public void schedule(UUID tenantId, UUID courseId, UUID itemId, Instant releaseAt) {
        jdbc.sql("""
                INSERT INTO quiz_grade_releases (tenant_id, item_id, course_id, release_at)
                VALUES (:tenantId, :itemId, :courseId, :releaseAt)
                ON CONFLICT (tenant_id, item_id) DO UPDATE SET
                    released_at = CASE WHEN quiz_grade_releases.release_at = EXCLUDED.release_at
                                       THEN quiz_grade_releases.released_at END,
                    release_at = EXCLUDED.release_at
                """)
            .param("tenantId", tenantId).param("itemId", itemId).param("courseId", courseId)
            .param("releaseAt", Timestamps.of(releaseAt))
            .update();
    }

    @Override
    public void cancel(UUID tenantId, UUID itemId) {
        jdbc.sql("DELETE FROM quiz_grade_releases WHERE tenant_id = :tenantId AND item_id = :itemId AND released_at IS NULL")
            .param("tenantId", tenantId).param("itemId", itemId)
            .update();
    }

    @Override
    public List<Release> findDue(Instant now, int limit) {
        return jdbc.sql("""
                SELECT tenant_id, course_id, item_id FROM quiz_grade_releases
                WHERE released_at IS NULL AND release_at <= :now
                ORDER BY release_at LIMIT :limit
                """)
            .param("now", Timestamps.of(now)).param("limit", limit)
            .query((rs, n) -> new Release(rs.getObject("tenant_id", UUID.class), rs.getObject("course_id", UUID.class),
                    rs.getObject("item_id", UUID.class)))
            .list();
    }

    @Override
    public boolean markReleased(UUID tenantId, UUID itemId, Instant at) {
        return jdbc.sql("""
                UPDATE quiz_grade_releases SET released_at = :at
                WHERE tenant_id = :tenantId AND item_id = :itemId AND released_at IS NULL
                """)
            .param("at", Timestamps.of(at)).param("tenantId", tenantId).param("itemId", itemId)
            .update() == 1;
    }
}
