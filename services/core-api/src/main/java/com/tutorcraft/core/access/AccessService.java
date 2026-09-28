package com.tutorcraft.core.access;

import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.access.domain.TenantRole;
import java.util.Set;
import java.util.UUID;

/**
 * Публичный API модуля access: единая точка авторизации (FR-ACL-02).
 * Вызывается из application-слоя модулей; контроллеры и UI не проверяют роли по имени.
 */
public interface AccessService {

    /** Права текущего пользователя в контексте. Курс чужого tenant → NotFoundException (AC-1). */
    Set<Permission> permissions(AccessContext context);

    boolean can(Permission permission, AccessContext context);

    /** Бросает ForbiddenException (или NotFound для чужого/несуществующего курса). */
    void require(Permission permission, AccessContext context);

    /** То же для произвольного пользователя (фоновые задачи, отчёты). */
    Set<Permission> permissionsOf(UUID tenantId, UUID userId, AccessContext context);

    Set<TenantRole> tenantRoles(UUID tenantId, UUID userId);

    /** Заменяет роли уровня tenant (tenant_admin и т.п.). category_manager назначается отдельно. */
    void replaceTenantRoles(UUID tenantId, UUID userId, Set<TenantRole> roles, UUID actorId);

    /**
     * Делает пользователя единственным главным администратором (platform_admin): роль назначается ему и
     * снимается со всех остальных. Вызывается только при старте из конфигурации (ADMIN_EMAIL), не через API.
     */
    void ensureSolePlatformAdmin(UUID tenantId, UUID userId);

    void assignCategoryManager(UUID tenantId, UUID userId, UUID categoryId, UUID actorId);
}
