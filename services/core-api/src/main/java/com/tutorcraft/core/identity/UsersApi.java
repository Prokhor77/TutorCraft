package com.tutorcraft.core.identity;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Публичный API модуля identity. */
public interface UsersApi {

    Optional<UserRef> find(UUID tenantId, UUID userId);

    /** @throws com.tutorcraft.core.shared.domain.NotFoundException code {@code user.not_found} */
    UserRef require(UUID tenantId, UUID userId);

    Map<UUID, UserRef> findAll(UUID tenantId, Collection<UUID> userIds);

    Optional<UserRef> findByEmail(UUID tenantId, String email);

    /** @param status действующий статус: заблокированный платформой пользователь отдаётся как {@code suspended} */
    record UserRef(UUID id, UUID tenantId, String email, String firstName, String lastName, String locale,
                   String timezone, String status, Long telegramChatId, UUID avatarFileId) {

        public String displayName() {
            return (firstName + " " + lastName).trim();
        }
    }
}
