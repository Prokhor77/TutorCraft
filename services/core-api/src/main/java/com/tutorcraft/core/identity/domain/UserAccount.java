package com.tutorcraft.core.identity.domain;

import java.time.Instant;
import java.util.UUID;

/** Учётная запись пользователя tenant. passwordHash не покидает модуль identity. */
public record UserAccount(UUID id, UUID tenantId, String email, String passwordHash, String firstName, String lastName,
                          UUID avatarFileId, String timezone, String locale, UserStatus status, String googleSub,
                          Long telegramUserId, Long telegramChatId, Instant lastLoginAt, Instant createdAt, long version) {

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean isInvited() {
        return status == UserStatus.INVITED;
    }

    public boolean isSuspended() {
        return status == UserStatus.SUSPENDED;
    }

    public boolean hasPassword() {
        return passwordHash != null;
    }

    public boolean telegramLinked() {
        return telegramChatId != null;
    }
}
