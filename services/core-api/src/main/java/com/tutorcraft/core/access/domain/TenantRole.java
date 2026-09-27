package com.tutorcraft.core.access.domain;

import com.tutorcraft.core.shared.domain.ValidationException;
import java.util.Arrays;

/** Роли уровня инсталляции/tenant/категории, назначаемые через role_assignments. */
public enum TenantRole {
    PLATFORM_ADMIN(SystemRole.PLATFORM_ADMIN), TENANT_ADMIN(SystemRole.TENANT_ADMIN), CATEGORY_MANAGER(SystemRole.CATEGORY_MANAGER);

    private final SystemRole systemRole;

    TenantRole(SystemRole systemRole) {
        this.systemRole = systemRole;
    }

    public String key() {
        return systemRole.key();
    }

    public SystemRole systemRole() {
        return systemRole;
    }

    public static java.util.Optional<TenantRole> find(String key) {
        return Arrays.stream(values()).filter(role -> role.key().equals(key)).findFirst();
    }

    public static TenantRole fromKey(String key) {
        return Arrays.stream(values()).filter(role -> role.key().equals(key)).findFirst()
                .orElseThrow(() -> ValidationException.single("tenantRoles", "invalid_role", "Unknown tenant role"));
    }
}
