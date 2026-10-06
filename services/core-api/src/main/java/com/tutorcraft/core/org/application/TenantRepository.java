package com.tutorcraft.core.org.application;

import com.tutorcraft.core.org.OrgApi.TenantInfo;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface TenantRepository {

    boolean slugExists(String slug);

    void insert(UUID id, String slug, String name);

    Optional<TenantInfo> findById(UUID id);

    Optional<TenantInfo> findBySlug(String slug);

    /** Школы платформы (без служебного tenant главного администратора), новые сверху. */
    List<TenantSummary> list(String query, int limit);

    Optional<TenantSettingsView> settings(UUID id);

    /** Белый список хостов встраиваний; пусто — tenant не найден или список пуст. */
    Set<String> embedWhitelist(UUID id);

    /** Квота хранилища в МБ; пусто — без ограничения или tenant не найден. */
    Optional<Long> storageQuotaMb(UUID id);

    /** @return false, если версия не совпала (оптимистичная блокировка). */
    boolean updateSettings(UUID id, long expectedVersion, TenantSettingsUpdate update);

    record TenantSettingsView(UUID id, String slug, String name, UUID logoFileId, String primaryColor, String defaultLocale,
                              String defaultTimezone, com.tutorcraft.core.org.OrgApi.PasswordPolicy passwordPolicy,
                              List<String> embedWhitelist, long version) {
    }

    /** storageQuotaMb — квота хранилища школы; null — без ограничения. */
    record TenantSummary(UUID id, String slug, String name, String status, java.time.Instant createdAt, long usersCount,
                         Long storageQuotaMb) {
    }

    record TenantSettingsUpdate(String name, UUID logoFileId, String primaryColor, String defaultLocale,
                                String defaultTimezone, com.tutorcraft.core.org.OrgApi.PasswordPolicy passwordPolicy,
                                List<String> embedWhitelist) {
    }
}
