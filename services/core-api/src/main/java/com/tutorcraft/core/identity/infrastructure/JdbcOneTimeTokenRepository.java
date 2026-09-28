package com.tutorcraft.core.identity.infrastructure;

import com.tutorcraft.core.identity.application.OneTimeTokenRepository;
import com.tutorcraft.core.shared.persistence.Timestamps;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Три таблицы одноразовых токенов одинаковой формы; имя таблицы — из фиксированного перечня (не из ввода). */
@Repository
class JdbcOneTimeTokenRepository implements OneTimeTokenRepository {

    private static final Map<Kind, String> TABLES = Map.of(
            Kind.PASSWORD_RESET, "password_reset_tokens",
            Kind.INVITATION, "invitations",
            Kind.TELEGRAM_LINK, "telegram_link_codes");

    private final JdbcClient jdbc;

    JdbcOneTimeTokenRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Kind kind, NewToken token) {
        jdbc.sql("INSERT INTO " + table(kind) + " (id, tenant_id, user_id, token_hash, created_at, expires_at) "
                + "VALUES (:id, :tenantId, :userId, :hash, :createdAt, :expiresAt)")
            .param("id", token.id()).param("tenantId", token.tenantId()).param("userId", token.userId())
            .param("hash", token.tokenHash())
            .param("createdAt", Timestamps.of(token.createdAt())).param("expiresAt", Timestamps.of(token.expiresAt()))
            .update();
    }

    @Override
    public Optional<OneTimeToken> findValid(Kind kind, String tokenHash, Instant now) {
        return jdbc.sql("SELECT id, tenant_id, user_id FROM " + table(kind)
                + " WHERE token_hash = :hash AND used_at IS NULL AND expires_at > :now")
            .param("hash", tokenHash).param("now", Timestamps.of(now))
            .query((rs, n) -> new OneTimeToken(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                    rs.getObject("user_id", UUID.class)))
            .optional();
    }

    @Override
    public boolean consume(Kind kind, UUID tokenId, Instant now) {
        return jdbc.sql("UPDATE " + table(kind) + " SET used_at = :now WHERE id = :id AND used_at IS NULL")
            .param("now", Timestamps.of(now)).param("id", tokenId)
            .update() == 1;
    }

    @Override
    public int invalidateOutstanding(Kind kind, UUID tenantId, UUID userId, Instant now) {
        return jdbc.sql("UPDATE " + table(kind) + " SET used_at = :now "
                + "WHERE tenant_id = :tenantId AND user_id = :userId AND used_at IS NULL")
            .param("now", Timestamps.of(now)).param("tenantId", tenantId).param("userId", userId)
            .update();
    }

    private static String table(Kind kind) {
        return TABLES.get(kind);
    }
}
