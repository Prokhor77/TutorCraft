package com.tutorcraft.core.activity.application;

import java.util.UUID;

/**
 * Какие записи видны: школы {@code tenantId} и, для главного администратора платформы по запросу, анонимные
 * (вход, регистрация, публичные страницы — до аутентификации школа неизвестна).
 */
public record ActivityScope(UUID tenantId, boolean includeAnonymous) {
}
