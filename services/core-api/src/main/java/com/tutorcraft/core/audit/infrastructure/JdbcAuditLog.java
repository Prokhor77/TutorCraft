package com.tutorcraft.core.audit.infrastructure;

import com.tutorcraft.core.audit.AuditLog;
import com.tutorcraft.core.audit.AuditRecord;
import com.tutorcraft.core.shared.domain.Ids;
import com.tutorcraft.core.shared.persistence.JsonCodec;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
class JdbcAuditLog implements AuditLog {

    private static final int MAX_USER_AGENT_LENGTH = 300;

    private final JdbcClient jdbc;
    private final JsonCodec json;
    private final Clock clock;

    JdbcAuditLog(JdbcClient jdbc, JsonCodec json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    @Override
    public void record(AuditRecord record) {
        Optional<HttpServletRequest> request = currentRequest();
        jdbc.sql("""
                INSERT INTO audit_log (id, tenant_id, actor_id, action, object_type, object_id, context, ip, user_agent, diff, at)
                VALUES (:id, :tenantId, :actorId, :action, :objectType, :objectId, :context, :ip, :userAgent, :diff, :at)
                """)
            .param("id", Ids.newId())
            .param("tenantId", record.tenantId())
            .param("actorId", record.actorId())
            .param("action", record.action())
            .param("objectType", record.objectType())
            .param("objectId", record.objectId())
            .param("context", record.context())
            .param("ip", request.map(HttpServletRequest::getRemoteAddr).orElse(null))
            .param("userAgent", request.map(JdbcAuditLog::userAgent).orElse(null))
            .param("diff", record.diff() == null ? null : json.toJsonb(record.diff()))
            .param("at", Timestamp.from(clock.instant()))
            .update();
    }

    private static String userAgent(HttpServletRequest request) {
        String value = request.getHeader("User-Agent");
        if (value == null) {
            return null;
        }
        return value.length() > MAX_USER_AGENT_LENGTH ? value.substring(0, MAX_USER_AGENT_LENGTH) : value;
    }

    private static Optional<HttpServletRequest> currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return Optional.of(attributes.getRequest());
        }
        return Optional.empty();
    }
}
