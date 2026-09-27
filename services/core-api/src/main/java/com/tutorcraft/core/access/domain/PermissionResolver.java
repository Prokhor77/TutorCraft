package com.tutorcraft.core.access.domain;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Чистая функция расчёта итоговых прав (FR-ACL-02). Без ввода-вывода — 100% unit-тестируема.
 *
 * @param rolePermissions права по ключу роли (системные + кастомные роли tenant)
 */
public final class PermissionResolver {

    private static final String PLATFORM = "platform";
    private static final String TENANT = "tenant";
    private static final String CATEGORY = "category";

    private final Map<String, Set<Permission>> rolePermissions;

    public PermissionResolver(Map<String, Set<Permission>> rolePermissions) {
        this.rolePermissions = Map.copyOf(rolePermissions);
    }

    /**
     * @param grants             назначения tenant/категорийных ролей пользователя
     * @param categoryAncestry   категория контекста и все её предки (пусто для TENANT-контекста)
     * @param courseRoleKey      роль активной записи на курс, если контекст — курс
     */
    public Set<Permission> resolve(Collection<RoleGrant> grants, List<UUID> categoryAncestry, Optional<String> courseRoleKey) {
        EnumSet<Permission> result = EnumSet.noneOf(Permission.class);
        for (RoleGrant grant : grants) {
            if (appliesTo(grant, categoryAncestry)) {
                result.addAll(permissionsOf(grant.roleKey()));
            }
        }
        courseRoleKey.ifPresent(role -> result.addAll(permissionsOf(role)));
        return result;
    }

    private static boolean appliesTo(RoleGrant grant, List<UUID> categoryAncestry) {
        return switch (grant.contextType()) {
            case PLATFORM, TENANT -> true;
            case CATEGORY -> categoryAncestry.contains(grant.contextId());
            default -> false;
        };
    }

    private Set<Permission> permissionsOf(String roleKey) {
        return rolePermissions.getOrDefault(roleKey, Set.of());
    }
}
