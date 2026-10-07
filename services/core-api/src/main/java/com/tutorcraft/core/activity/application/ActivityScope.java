package com.tutorcraft.core.activity.application;

import java.util.UUID;

/**
 * Какие записи видны: школы {@code tenantId} либо, для главного администратора платформы, всех школ
 * ({@code allTenants}) и, по запросу, анонимные (вход, регистрация, публичные страницы — до аутентификации школа
 * неизвестна).
 */
public record ActivityScope(UUID tenantId, boolean includeAnonymous, boolean allTenants) {

    public ActivityScope(UUID tenantId, boolean includeAnonymous) {
        this(tenantId, includeAnonymous, false);
    }
}
