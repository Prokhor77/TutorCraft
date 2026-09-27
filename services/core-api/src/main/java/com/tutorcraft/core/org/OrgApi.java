package com.tutorcraft.core.org;

import java.util.Optional;
import java.util.UUID;

/** Публичный API модуля org для других модулей. */
public interface OrgApi {

    TenantInfo createTenant(String name, String slugHint);

    Optional<TenantInfo> findBySlug(String slug);

    TenantInfo require(UUID tenantId);

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
