package com.tutorcraft.core.shared.security;

import java.util.Set;
import java.util.UUID;

/** Аутентифицированный пользователь запроса. Роли из токена — только для UI; решения о доступе — AccessService. */
public record CurrentUser(UUID userId, UUID tenantId, Set<String> tenantRoles) {
}
