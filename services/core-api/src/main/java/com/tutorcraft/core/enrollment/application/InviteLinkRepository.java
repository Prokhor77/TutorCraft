package com.tutorcraft.core.enrollment.application;

import com.tutorcraft.core.enrollment.domain.InviteLink;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Ссылки-приглашения (PostgreSQL). */
public interface InviteLinkRepository {

    void insert(InviteLink link);

    Optional<InviteLink> find(UUID tenantId, UUID id);

    /** Поиск по хешу токена в пределах tenant пользователя (чужой tenant → пусто). */
    Optional<InviteLink> findByTokenHash(UUID tenantId, String tokenHash);

    List<InviteLink> listByCourse(UUID tenantId, UUID courseId);

    void revoke(UUID tenantId, UUID id, Instant now);

    /** Атомарно увеличивает счётчик, если ссылка всё ещё действует; false — исчерпана/истекла/отозвана. */
    boolean consume(UUID tenantId, UUID id, Instant now);
}
