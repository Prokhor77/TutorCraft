package com.tutorcraft.core.assessment.quiz.infrastructure;

import com.tutorcraft.core.assessment.quiz.application.QuizOverrideRepository;
import com.tutorcraft.core.assessment.quiz.domain.QuizOverride;
import com.tutorcraft.core.shared.persistence.Timestamps;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcQuizOverrideRepository implements QuizOverrideRepository {

    private static final String COLUMNS = "id, user_id, group_id, open_at, close_at, time_limit_sec, max_attempts";

    private final JdbcClient jdbc;

    JdbcQuizOverrideRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public QuizOverride upsert(UUID tenantId, UUID courseId, UUID itemId, QuizOverride override, UUID actorId) {
        String conflictTarget = override.forUser()
                ? "(tenant_id, item_id, user_id) WHERE user_id IS NOT NULL"
                : "(tenant_id, item_id, group_id) WHERE group_id IS NOT NULL";
        return jdbc.sql("""
                INSERT INTO quiz_overrides (id, tenant_id, course_id, item_id, user_id, group_id, open_at, close_at,
                                            time_limit_sec, max_attempts, created_by, created_at)
                VALUES (:id, :tenantId, :courseId, :itemId, :userId, :groupId, :openAt, :closeAt, :timeLimit, :maxAttempts,
                        :actorId, now())
                ON CONFLICT %s DO UPDATE SET open_at = EXCLUDED.open_at, close_at = EXCLUDED.close_at,
                    time_limit_sec = EXCLUDED.time_limit_sec, max_attempts = EXCLUDED.max_attempts,
                    created_by = EXCLUDED.created_by, created_at = EXCLUDED.created_at
                RETURNING %s
                """.formatted(conflictTarget, COLUMNS))
            .param("id", override.id()).param("tenantId", tenantId).param("courseId", courseId).param("itemId", itemId)
            .param("userId", override.userId()).param("groupId", override.groupId())
            .param("openAt", Timestamps.of(override.openAt())).param("closeAt", Timestamps.of(override.closeAt()))
            .param("timeLimit", override.timeLimitSec()).param("maxAttempts", override.maxAttempts())
            .param("actorId", actorId)
            .query((rs, n) -> toOverride(rs)).single();
    }

    @Override
    public List<QuizOverride> listOfItem(UUID tenantId, UUID itemId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM quiz_overrides WHERE tenant_id = :tenantId AND item_id = :itemId ORDER BY created_at")
                .param("tenantId", tenantId).param("itemId", itemId)
                .query((rs, n) -> toOverride(rs)).list();
    }

    @Override
    public List<QuizOverride> applicable(UUID tenantId, UUID itemId, UUID userId, Collection<UUID> groupIds) {
        String groupCondition = groupIds.isEmpty() ? "" : " OR group_id IN (:groupIds)";
        JdbcClient.StatementSpec statement = jdbc.sql("SELECT " + COLUMNS + """
                 FROM quiz_overrides
                WHERE tenant_id = :tenantId AND item_id = :itemId AND (user_id = :userId%s)
                """.formatted(groupCondition))
            .param("tenantId", tenantId).param("itemId", itemId).param("userId", userId);
        if (!groupIds.isEmpty()) {
            statement = statement.param("groupIds", List.copyOf(groupIds));
        }
        return statement.query((rs, n) -> toOverride(rs)).list();
    }

    @Override
    public boolean delete(UUID tenantId, UUID itemId, UUID overrideId) {
        return jdbc.sql("DELETE FROM quiz_overrides WHERE tenant_id = :tenantId AND item_id = :itemId AND id = :id")
                .param("tenantId", tenantId).param("itemId", itemId).param("id", overrideId)
                .update() == 1;
    }

    private static QuizOverride toOverride(ResultSet rs) throws SQLException {
        return new QuizOverride(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                rs.getObject("group_id", UUID.class), Timestamps.read(rs, "open_at"), Timestamps.read(rs, "close_at"),
                rs.getObject("time_limit_sec", Integer.class), rs.getObject("max_attempts", Integer.class));
    }
}
