package com.tutorcraft.core.identity.domain;

import java.time.Instant;
import java.util.UUID;

/** Учётная запись пользователя tenant. passwordHash не покидает модуль identity; status — блокировка школой. */
public record UserAccount(UUID id, UUID tenantId, String email, String passwordHash, String firstName, String lastName,
                          UUID avatarFileId, String timezone, String locale, UserStatus status, String googleSub,
                          Long telegramUserId, Long telegramChatId, Instant lastLoginAt, Instant createdAt, long version,
                          Instant platformBlockedAt) {

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean isInvited() {
        return status == UserStatus.INVITED;
    }

    public boolean isSuspended() {
        return status == UserStatus.SUSPENDED;
    }

    /** Заблокирован главным администратором платформы (независимо от блокировки школой). */
    public boolean isPlatformBlocked() {
        return platformBlockedAt != null;
    }

    public boolean hasPassword() {
        return passwordHash != null;
    }

    public boolean telegramLinked() {
        return telegramChatId != null;
    }
}
