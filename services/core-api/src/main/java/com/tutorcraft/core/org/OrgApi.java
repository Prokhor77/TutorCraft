package com.tutorcraft.core.org;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Публичный API модуля org для других модулей. */
public interface OrgApi {

    /**
     * Служебный tenant главного администратора. Подчёркивание не порождается SlugGenerator, поэтому slug
     * не может совпасть со школой, зарегистрированной пользователем.
     */
    String PLATFORM_TENANT_SLUG = "platform_admin";

    TenantInfo createTenant(String name, String slugHint);

    /** Служебный tenant главного администратора; создаётся при первом вызове. */
    TenantInfo ensurePlatformTenant();

    Optional<TenantInfo> findBySlug(String slug);

    TenantInfo require(UUID tenantId);

    /**
     * Хосты, разрешённые для встраиваний (embed/iframe, video.embedUrl) в блочных документах tenant (FR-CONTENT-01).
     * Пустое множество — встраивания запрещены (в том числе для несуществующего tenant).
     */
    Set<String> embedWhitelist(UUID tenantId);

    /** Квота хранилища файлов tenant в мегабайтах; пусто — без ограничения (или tenant не найден). */
    Optional<Long> storageQuotaMb(UUID tenantId);

    record TenantInfo(UUID id, String slug, String name, String status, String defaultLocale, String defaultTimezone,
                      UUID logoFileId, String primaryColor, PasswordPolicy passwordPolicy) {

        public boolean active() {
            return "active".equals(status);
        }
    }

    record PasswordPolicy(int minLength, boolean requireDigit, boolean requireLetter) {

        public static final int ABSOLUTE_MIN_LENGTH = 8;
        public static final int MAX_LENGTH = 128;
    }
}
