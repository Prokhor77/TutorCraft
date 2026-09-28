package com.tutorcraft.core.identity.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Одноразовые токены: сброс пароля, приглашения, коды привязки Telegram (хранятся хешем). */
public interface OneTimeTokenRepository {

    enum Kind { PASSWORD_RESET, INVITATION, TELEGRAM_LINK }

    void insert(Kind kind, NewToken token);

    /** Неиспользованный и не истёкший токен. */
    Optional<OneTimeToken> findValid(Kind kind, String tokenHash, Instant now);

    /** Атомарно помечает токен использованным. @return false, если его уже использовали. */
    boolean consume(Kind kind, UUID tokenId, Instant now);

    /** Аннулирует все неиспользованные токены пользователя этого вида. */
    int invalidateOutstanding(Kind kind, UUID tenantId, UUID userId, Instant now);

    record NewToken(UUID id, UUID tenantId, UUID userId, String tokenHash, Instant createdAt, Instant expiresAt) {
    }

    record OneTimeToken(UUID id, UUID tenantId, UUID userId) {
    }
}
