package com.tutorcraft.core.enrollment.infrastructure;

import com.tutorcraft.core.access.domain.CourseRole;
import com.tutorcraft.core.enrollment.application.InviteLinkRepository;
import com.tutorcraft.core.enrollment.domain.InviteLink;
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
class JdbcInviteLinkRepository implements InviteLinkRepository {

    private static final String SELECT = """
            SELECT id, tenant_id, course_id, token_hash, role_key, expires_at, max_uses, uses, revoked_at, created_by, created_at
            FROM course_invite_links
            """;

    private final JdbcClient jdbc;

    JdbcInviteLinkRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(InviteLink link) {
        jdbc.sql("""
                INSERT INTO course_invite_links (id, tenant_id, course_id, token_hash, role_key, expires_at, max_uses, uses,
                                                 created_by, created_at)
                VALUES (:id, :tenantId, :courseId, :tokenHash, :roleKey, :expiresAt, :maxUses, 0, :createdBy, :createdAt)
                """)
            .param("id", link.id()).param("tenantId", link.tenantId()).param("courseId", link.courseId())
            .param("tokenHash", link.tokenHash()).param("roleKey", link.role().key())
            .param("expiresAt", Timestamps.of(link.expiresAt())).param("maxUses", link.maxUses())
            .param("createdBy", link.createdBy()).param("createdAt", Timestamps.of(link.createdAt()))
            .update();
    }

    @Override
    public Optional<InviteLink> find(UUID tenantId, UUID id) {
        return jdbc.sql(SELECT + " WHERE tenant_id = :tenantId AND id = :id")
            .param("tenantId", tenantId).param("id", id)
            .query(JdbcInviteLinkRepository::map).optional();
    }

    @Override
    public Optional<InviteLink> findByTokenHash(UUID tenantId, String tokenHash) {
        return jdbc.sql(SELECT + " WHERE tenant_id = :tenantId AND token_hash = :tokenHash")
            .param("tenantId", tenantId).param("tokenHash", tokenHash)
            .query(JdbcInviteLinkRepository::map).optional();
    }

    @Override
    public List<InviteLink> listByCourse(UUID tenantId, UUID courseId) {
        return jdbc.sql(SELECT + " WHERE tenant_id = :tenantId AND course_id = :courseId ORDER BY created_at DESC")
            .param("tenantId", tenantId).param("courseId", courseId)
            .query(JdbcInviteLinkRepository::map).list();
    }

    @Override
    public void revoke(UUID tenantId, UUID id, Instant now) {
        jdbc.sql("UPDATE course_invite_links SET revoked_at = :now WHERE tenant_id = :tenantId AND id = :id AND revoked_at IS NULL")
            .param("now", Timestamps.of(now)).param("tenantId", tenantId).param("id", id).update();
    }

    @Override
    public boolean consume(UUID tenantId, UUID id, Instant now) {
        return jdbc.sql("""
                UPDATE course_invite_links SET uses = uses + 1
                WHERE tenant_id = :tenantId AND id = :id AND revoked_at IS NULL
                  AND (expires_at IS NULL OR expires_at > :now) AND (max_uses IS NULL OR uses < max_uses)
                """)
            .param("tenantId", tenantId).param("id", id).param("now", Timestamps.of(now))
            .update() == 1;
    }

    private static InviteLink map(ResultSet rs, int rowNum) throws SQLException {
        int maxUses = rs.getInt("max_uses");
        Integer maxUsesOrNull = rs.wasNull() ? null : maxUses;
        return new InviteLink(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getObject("course_id", UUID.class), rs.getString("token_hash"), CourseRole.fromKey(rs.getString("role_key")),
                Timestamps.read(rs, "expires_at"), maxUsesOrNull, rs.getInt("uses"), Timestamps.read(rs, "revoked_at"),
                rs.getObject("created_by", UUID.class), Timestamps.read(rs, "created_at"));
    }
}
