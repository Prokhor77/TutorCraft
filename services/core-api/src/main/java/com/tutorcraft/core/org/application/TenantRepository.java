package com.tutorcraft.core.org.application;

import com.tutorcraft.core.org.OrgApi.TenantInfo;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantRepository {

    boolean slugExists(String slug);

    void insert(UUID id, String slug, String name);

    Optional<TenantInfo> findById(UUID id);

    Optional<TenantInfo> findBySlug(String slug);

    Optional<TenantSettingsView> settings(UUID id);

    /** @return false, если версия не совпала (оптимистичная блокировка). */
    boolean updateSettings(UUID id, long expectedVersion, TenantSettingsUpdate update);

    record TenantSettingsView(UUID id, String slug, String name, UUID logoFileId, String primaryColor, String defaultLocale,
                              String defaultTimezone, com.tutorcraft.core.org.OrgApi.PasswordPolicy passwordPolicy,
                              List<String> embedWhitelist, long version) {
    }

    record TenantSettingsUpdate(String name, UUID logoFileId, String primaryColor, String defaultLocale,
                                String defaultTimezone, com.tutorcraft.core.org.OrgApi.PasswordPolicy passwordPolicy,
                                List<String> embedWhitelist) {
    }
}
