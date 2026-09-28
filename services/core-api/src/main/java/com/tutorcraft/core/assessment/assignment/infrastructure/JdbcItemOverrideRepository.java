package com.tutorcraft.core.assessment.assignment.infrastructure;

import com.tutorcraft.core.assessment.assignment.application.ItemOverrideRepository;
import com.tutorcraft.core.assessment.assignment.domain.ItemOverride;
import com.tutorcraft.core.shared.persistence.Timestamps;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcItemOverrideRepository implements ItemOverrideRepository {

    private static final String COLUMNS = """
            id, tenant_id, course_id, item_id, user_id, group_id, due_at, close_at, created_by, created_at
            """;
    /** Несуществующий UUID для пустого списка групп в IN (...). */
    private static final UUID NO_GROUP = new UUID(0L, 0L);

    private final JdbcClient jdbc;

    JdbcItemOverrideRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ItemOverride upsert(ItemOverride override) {
        String conflictTarget = override.forUser()
                ? "(tenant_id, item_id, user_id) WHERE user_id IS NOT NULL"
                : "(tenant_id, item_id, group_id) WHERE group_id IS NOT NULL";
        return jdbc.sql("""
                INSERT INTO item_overrides (id, tenant_id, course_id, item_id, user_id, group_id, due_at, close_at,
                                            created_by, created_at, updated_at)
                VALUES (:id, :tenantId, :courseId, :itemId, :userId, :groupId, :dueAt, :closeAt, :createdBy, :now, :now)
                ON CONFLICT\s""" + conflictTarget + """
                 DO UPDATE SET due_at = EXCLUDED.due_at, close_at = EXCLUDED.close_at,
                               created_by = EXCLUDED.created_by, updated_at = EXCLUDED.updated_at
                RETURNING\s""" + COLUMNS)
            .param("id", override.id()).param("tenantId", override.tenantId()).param("courseId", override.courseId())
            .param("itemId", override.itemId()).param("userId", override.userId()).param("groupId", override.groupId())
            .param("dueAt", Timestamps.of(override.dueAt())).param("closeAt", Timestamps.of(override.closeAt()))
            .param("createdBy", override.createdBy()).param("now", Timestamps.of(override.createdAt()))
            .query((rs, n) -> toOverride(rs)).single();
    }

    @Override
    public List<ItemOverride> ofItem(UUID tenantId, UUID itemId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM item_overrides WHERE tenant_id = :tenantId AND item_id = :itemId ORDER BY created_at")
                .param("tenantId", tenantId).param("itemId", itemId)
                .query((rs, n) -> toOverride(rs)).list();
    }

    @Override
    public List<ItemOverride> applicable(UUID tenantId, UUID itemId, UUID userId, Collection<UUID> groupIds) {
        List<UUID> groups = groupIds.isEmpty() ? List.of(NO_GROUP) : List.copyOf(groupIds);
        return jdbc.sql("SELECT " + COLUMNS + """
                 FROM item_overrides
                WHERE tenant_id = :tenantId AND item_id = :itemId AND (user_id = :userId OR group_id IN (:groupIds))
                """)
            .param("tenantId", tenantId).param("itemId", itemId).param("userId", userId).param("groupIds", groups)
            .query((rs, n) -> toOverride(rs)).list();
    }

    @Override
    public Optional<ItemOverride> find(UUID tenantId, UUID overrideId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM item_overrides WHERE tenant_id = :tenantId AND id = :id")
                .param("tenantId", tenantId).param("id", overrideId)
                .query((rs, n) -> toOverride(rs)).optional();
    }

    @Override
    public void delete(UUID tenantId, UUID overrideId) {
        jdbc.sql("DELETE FROM item_overrides WHERE tenant_id = :tenantId AND id = :id")
            .param("tenantId", tenantId).param("id", overrideId).update();
    }

    private static ItemOverride toOverride(ResultSet rs) throws SQLException {
        return new ItemOverride(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getObject("course_id", UUID.class), rs.getObject("item_id", UUID.class),
                rs.getObject("user_id", UUID.class), rs.getObject("group_id", UUID.class),
                Timestamps.read(rs, "due_at"), Timestamps.read(rs, "close_at"),
                rs.getObject("created_by", UUID.class), Timestamps.read(rs, "created_at"));
    }
}
