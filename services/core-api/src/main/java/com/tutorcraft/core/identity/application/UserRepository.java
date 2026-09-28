package com.tutorcraft.core.identity.application;

import com.tutorcraft.core.identity.domain.UserAccount;
import com.tutorcraft.core.identity.domain.UserStatus;
import com.tutorcraft.core.shared.api.PageQuery;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Хранилище пользователей. Методы с tenantId фильтруют по tenant (DATA-01); удалённые не возвращаются. */
public interface UserRepository {

    Optional<UserAccount> findById(UUID tenantId, UUID userId);

    List<UserAccount> findAll(UUID tenantId, Collection<UUID> userIds);

    Optional<UserAccount> findByEmail(UUID tenantId, String normalizedEmail);

    /** Все учётные записи с этим email во всех tenant (вход без tenantSlug). */
    List<UserAccount> findAllByEmail(String normalizedEmail);

    Optional<UserAccount> findByGoogleSub(UUID tenantId, String googleSub);

    List<UserAccount> findAllByGoogleSub(String googleSub);

    Optional<UserAccount> findByTelegramUserId(UUID tenantId, long telegramUserId);

    List<UserAccount> findAllByTelegramUserId(long telegramUserId);

    /** @return нормализованные email из списка, уже занятые в tenant */
    Set<String> existingEmails(UUID tenantId, Collection<String> normalizedEmails);

    void insert(NewUser user);

    void updatePassword(UUID tenantId, UUID userId, String passwordHash);

    void updateProfile(UUID tenantId, UUID userId, ProfileUpdate update);

    void updateStatus(UUID tenantId, UUID userId, UserStatus status);

    void updateNames(UUID tenantId, UUID userId, String firstName, String lastName);

    void activate(UUID tenantId, UUID userId, String passwordHash, String firstName, String lastName);

    void recordLogin(UUID tenantId, UUID userId, Instant at);

    void linkGoogle(UUID tenantId, UUID userId, String googleSub);

    /** null-значения не меняют соответствующие колонки. */
    void linkTelegram(UUID tenantId, UUID userId, Long telegramUserId, Long telegramChatId);

    List<UserSummaryView> search(UUID tenantId, UserFilter filter, PageQuery page);

    Optional<UserSummaryView> summary(UUID tenantId, UUID userId);

    record NewUser(UUID id, UUID tenantId, String email, String passwordHash, String firstName, String lastName,
                   String timezone, String locale, UserStatus status, String googleSub, Long telegramUserId) {
    }

    record ProfileUpdate(String firstName, String lastName, String timezone, String locale, UUID avatarFileId) {
    }

    record UserFilter(String query, String status, String roleKey) {
    }

    record UserSummaryView(UUID id, String email, String firstName, String lastName, String status,
                           List<String> tenantRoles, Instant lastLoginAt, Instant createdAt) {
    }
}
