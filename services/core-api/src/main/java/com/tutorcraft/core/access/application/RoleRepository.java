package com.tutorcraft.core.access.application;

import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.access.domain.RoleGrant;
import com.tutorcraft.core.access.domain.SystemRole;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface RoleRepository {

    /** Права всех ролей, доступных tenant (системные + кастомные). */
    Map<String, Set<Permission>> rolePermissions(UUID tenantId);

    List<RoleGrant> grantsOf(UUID tenantId, UUID userId);

    void syncSystemRole(SystemRole role);

    void deleteTenantGrants(UUID tenantId, UUID userId);

    /** Снимает platform-назначения со всех пользователей, кроме указанного. @return число снятых назначений */
    int deletePlatformGrantsExcept(UUID userId);

    void insertGrant(UUID tenantId, UUID userId, String roleKey, String contextType, UUID contextId, UUID actorId);
}
