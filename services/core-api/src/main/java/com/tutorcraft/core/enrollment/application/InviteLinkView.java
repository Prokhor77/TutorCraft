package com.tutorcraft.core.enrollment.application;

import java.time.Instant;
import java.util.UUID;

/** InviteLink (контракт §6) — без токена; active = ссылка действует сейчас. */
public record InviteLinkView(UUID id, String role, Instant expiresAt, Integer maxUses, int uses, Instant revokedAt,
                             Instant createdAt, boolean active) {

    /** Ответ на создание: токен входит в url и показывается один раз. */
    public record Created(UUID id, String url) {
    }
}
