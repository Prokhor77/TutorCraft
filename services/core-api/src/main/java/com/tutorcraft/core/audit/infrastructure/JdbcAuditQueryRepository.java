package com.tutorcraft.core.audit.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.tutorcraft.core.audit.application.AuditQueryRepository;
import com.tutorcraft.core.audit.application.AuditQueryService.AuditEntryView;
import com.tutorcraft.core.audit.application.AuditQueryService.AuditFilter;
import com.tutorcraft.core.shared.api.CursorCodec;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.persistence.JsonCodec;
import com.tutorcraft.core.shared.persistence.Timestamps;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAuditQueryRepository implements AuditQueryRepository {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };

    private final JdbcClient jdbc;
    private final JsonCodec json;

    JdbcAuditQueryRepository(JdbcClient jdbc, JsonCodec json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public List<AuditEntryView> search(UUID tenantId, AuditFilter filter, PageQuery page) {
        CursorCodec.Position after = page.after().orElse(null);
        return jdbc.sql("""
                SELECT a.id, a.at, a.tenant_id, t.name AS tenant_name, a.actor_id,
                       u.first_name || ' ' || u.last_name AS actor_name, a.action,
                       a.object_type, a.object_id, a.ip, a.diff::text AS diff
                FROM audit_log a LEFT JOIN users u ON u.id = a.actor_id LEFT JOIN tenants t ON t.id = a.tenant_id
                WHERE (:allTenants OR a.tenant_id = :tenantId)
                  AND (CAST(:actorId AS uuid) IS NULL OR a.actor_id = :actorId)
                  AND (CAST(:objectType AS text) IS NULL OR a.object_type = :objectType)
                  AND (CAST(:from AS timestamptz) IS NULL OR a.at >= :from)
                  AND (CAST(:to AS timestamptz) IS NULL OR a.at < :to)
                  AND (CAST(:afterAt AS timestamptz) IS NULL OR (a.at, a.id) < (:afterAt, :afterId))
                ORDER BY a.at DESC, a.id DESC
                LIMIT :limit
                """)
            .param("tenantId", tenantId)
            .param("allTenants", filter.allTenants())
            .param("actorId", filter.actorId())
            .param("objectType", filter.objectType())
            .param("from", Timestamps.of(filter.from()))
            .param("to", Timestamps.of(filter.to()))
            .param("afterAt", after == null ? null : Timestamps.of(after.sortKey()))
            .param("afterId", after == null ? null : after.id())
            .param("limit", page.fetchSize())
            .query((rs, n) -> new AuditEntryView(rs.getObject("id", UUID.class), Timestamps.read(rs, "at"),
                    rs.getObject("tenant_id", UUID.class), rs.getString("tenant_name"), rs.getObject("actor_id", UUID.class), rs.getString("actor_name"), rs.getString("action"),
                    rs.getString("object_type"), rs.getString("object_id"), rs.getString("ip"),
                    json.read(rs.getString("diff"), MAP)))
            .list();
    }
}
