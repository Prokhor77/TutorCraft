package com.tutorcraft.core.identity.infrastructure;

import com.tutorcraft.core.identity.application.RefreshTokenRepository;
import com.tutorcraft.core.identity.domain.RefreshTokenRecord;
import com.tutorcraft.core.shared.persistence.Timestamps;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcRefreshTokenRepository implements RefreshTokenRepository {

    private final JdbcClient jdbc;

    JdbcRefreshTokenRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(NewRefreshToken token) {
        jdbc.sql("""
                INSERT INTO refresh_tokens (id, tenant_id, user_id, family_id, token_hash, created_at, expires_at)
                VALUES (:id, :tenantId, :userId, :familyId, :hash, :createdAt, :expiresAt)
                """)
            .param("id", token.id()).param("tenantId", token.tenantId()).param("userId", token.userId())
            .param("familyId", token.familyId()).param("hash", token.tokenHash())
            .param("createdAt", Timestamps.of(token.createdAt())).param("expiresAt", Timestamps.of(token.expiresAt()))
            .update();
    }

    @Override
    public Optional<RefreshTokenRecord> findByHash(String tokenHash) {
        return jdbc.sql("""
                SELECT id, tenant_id, user_id, family_id, expires_at, revoked_at FROM refresh_tokens WHERE token_hash = :hash
                """)
            .param("hash", tokenHash)
            .query((rs, n) -> new RefreshTokenRecord(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                    rs.getObject("user_id", UUID.class), rs.getObject("family_id", UUID.class),
                    Timestamps.read(rs, "expires_at"), Timestamps.read(rs, "revoked_at")))
            .optional();
    }

    @Override
    public boolean revokeIfActive(UUID tokenId, Instant now, UUID replacedBy) {
        return jdbc.sql("""
                UPDATE refresh_tokens SET revoked_at = :now, replaced_by = :replacedBy
                WHERE id = :id AND revoked_at IS NULL
                """)
            .param("now", Timestamps.of(now)).param("replacedBy", replacedBy).param("id", tokenId)
            .update() == 1;
    }

    @Override
    public int revokeFamily(UUID familyId, Instant now) {
        return jdbc.sql("UPDATE refresh_tokens SET revoked_at = :now WHERE family_id = :familyId AND revoked_at IS NULL")
            .param("now", Timestamps.of(now)).param("familyId", familyId)
            .update();
    }

    @Override
    public int revokeAllOfUser(UUID tenantId, UUID userId, Instant now) {
        return jdbc.sql("""
                UPDATE refresh_tokens SET revoked_at = :now
                WHERE tenant_id = :tenantId AND user_id = :userId AND revoked_at IS NULL
                """)
            .param("now", Timestamps.of(now)).param("tenantId", tenantId).param("userId", userId)
            .update();
    }
}
