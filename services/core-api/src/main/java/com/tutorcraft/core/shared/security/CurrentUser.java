package com.tutorcraft.core.shared.security;

import java.util.Set;
import java.util.UUID;

/**
 * Аутентифицированный пользователь запроса. Роли из токена — только для UI; решения о доступе — AccessService.
 *
 * @param tenantId     tenant, в котором выполняется запрос (для главного администратора — выбранная школа)
 * @param homeTenantId tenant учётной записи пользователя; отличается от {@code tenantId}, только когда
 *                     главный администратор работает в чужой школе (заголовок {@value #TENANT_OVERRIDE_HEADER})
 */
public record CurrentUser(UUID userId, UUID tenantId, Set<String> tenantRoles, UUID homeTenantId) {

    public static final String TENANT_OVERRIDE_HEADER = "X-Tenant-Id";

    public CurrentUser(UUID userId, UUID tenantId, Set<String> tenantRoles) {
        this(userId, tenantId, tenantRoles, tenantId);
    }

    /** Главный администратор работает в чужой школе: права проверяются по его платформенной роли. */
    public boolean actsInForeignTenant() {
        return !tenantId.equals(homeTenantId);
    }
}
