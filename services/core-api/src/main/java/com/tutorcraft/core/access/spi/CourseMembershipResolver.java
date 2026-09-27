package com.tutorcraft.core.access.spi;

import java.util.Optional;
import java.util.UUID;

/**
 * Порт: роль пользователя в курсе по активной записи. Реализует модуль enrollment
 * (адаптер поверх репозитория — без обратной зависимости от AccessService, чтобы не было циклов).
 */
public interface CourseMembershipResolver {

    /** Ключ роли активной записи (status=active и сейчас в [starts_at, ends_at)), иначе пусто. */
    Optional<String> activeRoleKey(UUID tenantId, UUID userId, UUID courseId);
}
