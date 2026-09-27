package com.tutorcraft.core.access.infrastructure;

import com.tutorcraft.core.access.application.RoleRepository;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.access.domain.RoleGrant;
import com.tutorcraft.core.access.domain.SystemRole;
import com.tutorcraft.core.shared.domain.Ids;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcRoleRepository implements RoleRepository {

    private final JdbcClient jdbc;
    private final Clock clock;

    JdbcRoleRepository(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public Map<String, Set<Permission>> rolePermissions(UUID tenantId) {
        Map<String, Set<Permission>> result = new HashMap<>();
        jdbc.sql("""
                SELECT r.key, rp.permission FROM roles r
                JOIN role_permissions rp ON rp.role_id = r.id
                WHERE r.tenant_id IS NULL OR r.tenant_id = :tenantId
                """)
            .param("tenantId", tenantId)
            .query((rs, n) -> Map.entry(rs.getString("key"), rs.getString("permission")))
            .list()
            .forEach(entry -> Permission.fromKey(entry.getValue()).ifPresent(permission ->
                    result.computeIfAbsent(entry.getKey(), key -> EnumSet.noneOf(Permission.class)).add(permission)));
        return result;
    }

    @Override
    public List<RoleGrant> grantsOf(UUID tenantId, UUID userId) {
        return jdbc.sql("""
                SELECT r.key, ra.context_type, ra.context_id FROM role_assignments ra
                JOIN roles r ON r.id = ra.role_id
                WHERE ra.tenant_id = :tenantId AND ra.user_id = :userId
                """)
            .param("tenantId", tenantId).param("userId", userId)
            .query((rs, n) -> new RoleGrant(rs.getString("key"), rs.getString("context_type"),
                    rs.getObject("context_id", UUID.class)))
            .list();
    }

    @Override
    public void syncSystemRole(SystemRole role) {
        UUID roleId = jdbc.sql("""
                INSERT INTO roles (id, tenant_id, key, name, scope, is_system, created_at)
                VALUES (:id, NULL, :key, :key, :scope, true, :now)
                ON CONFLICT (key) WHERE tenant_id IS NULL DO UPDATE SET scope = EXCLUDED.scope
                RETURNING id
                """)
            .param("id", Ids.newId()).param("key", role.key()).param("scope", role.scope().key())
            .param("now", Timestamp.from(clock.instant()))
            .query(UUID.class).single();
        jdbc.sql("DELETE FROM role_permissions WHERE role_id = :roleId").param("roleId", roleId).update();
        role.permissions().forEach(permission -> jdbc.sql(
                "INSERT INTO role_permissions (role_id, permission) VALUES (:roleId, :permission)")
            .param("roleId", roleId).param("permission", permission.key()).update());
    }

    @Override
    public void deleteTenantGrants(UUID tenantId, UUID userId) {
        jdbc.sql("DELETE FROM role_assignments WHERE tenant_id = :tenantId AND user_id = :userId AND context_type = 'tenant'")
            .param("tenantId", tenantId).param("userId", userId).update();
    }

    @Override
    public void insertGrant(UUID tenantId, UUID userId, String roleKey, String contextType, UUID contextId, UUID actorId) {
        jdbc.sql("""
                INSERT INTO role_assignments (id, tenant_id, user_id, role_id, context_type, context_id, created_at, created_by)
                SELECT :id, :tenantId, :userId, r.id, :contextType, :contextId, :now, :actorId
                FROM roles r WHERE r.key = :roleKey AND r.tenant_id IS NULL
                ON CONFLICT DO NOTHING
                """)
            .param("id", Ids.newId()).param("tenantId", tenantId).param("userId", userId)
            .param("contextType", contextType).param("contextId", contextId)
            .param("now", Timestamp.from(clock.instant())).param("actorId", actorId).param("roleKey", roleKey)
            .update();
    }
}
