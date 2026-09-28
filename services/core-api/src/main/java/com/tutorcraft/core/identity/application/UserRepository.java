package com.tutorcraft.core.identity.application;

import com.tutorcraft.core.access.domain.TenantRole;
import com.tutorcraft.core.identity.domain.AccountOrigin;
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

    /** Учётная запись в любом tenant (операции главного администратора платформы). */
    Optional<UserAccount> findAnyById(UUID userId);

    /** Участники с происхождением и блокировками; {@code filter.tenantId() == null} — по всем tenant (платформа). */
    List<MemberView> searchMembers(MemberFilter filter, PageQuery page);

    /** @param tenantId null — в любом tenant */
    Optional<MemberView> member(UUID tenantId, UUID userId);

    /** @param at null — снять блокировку платформой */
    void updatePlatformBlock(UUID tenantId, UUID userId, Instant at, String reason);

    /**
     * Обезличивает учётную запись и помечает удалённой: строка остаётся только как цель внешних ключей чужих данных
     * (выставленные оценки, заказы, групповые сдачи).
     */
    void anonymize(UUID tenantId, UUID userId, Anonymized replacement, Instant at);

    record NewUser(UUID id, UUID tenantId, String email, String passwordHash, String firstName, String lastName,
                   String timezone, String locale, UserStatus status, String googleSub, Long telegramUserId,
                   UUID createdBy, AccountOrigin origin) {
    }

    record ProfileUpdate(String firstName, String lastName, String timezone, String locale, UUID avatarFileId) {
    }

    /** @param usableOnly только те, кто может войти (нет блокировки платформой) — для выбора при записи на курс */
    record UserFilter(String query, String status, String roleKey, boolean usableOnly) {

        public UserFilter(String query, String status, String roleKey) {
            this(query, status, roleKey, false);
        }
    }

    /**
     * @param tenantId null — все tenant
     * @param status   {@code active | suspended | invited} (статус в школе) или {@code blocked} (блокировка платформой);
     *                 {@code active} не включает заблокированных платформой
     * @param origin   ключ {@link AccountOrigin}
     */
    record MemberFilter(UUID tenantId, String query, String status, String origin) {
    }

    record CreatorView(UUID id, String email, String firstName, String lastName) {
    }

    record MemberView(UUID id, UUID tenantId, String email, String firstName, String lastName, String status,
                      Instant platformBlockedAt, String platformBlockReason, String origin, CreatorView createdBy,
                      List<String> tenantRoles, Instant lastLoginAt, Instant createdAt) {

        public boolean isTenantAdmin() {
            return tenantRoles.contains(TenantRole.TENANT_ADMIN.key());
        }
    }

    record Anonymized(String email, String firstName, String lastName) {
    }

    record UserSummaryView(UUID id, String email, String firstName, String lastName, String status,
                           List<String> tenantRoles, Instant lastLoginAt, Instant createdAt) {
    }
}
