package com.tutorcraft.core.courses.application;

import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.shared.domain.ForbiddenException;
import java.util.Map;
import java.util.Set;

/** Проверка уже вычисленного набора прав (одно обращение к AccessService на запрос). */
final class PermissionChecks {

    static final String ACCESS_DENIED = "access.denied";

    private PermissionChecks() {
    }

    static void require(Set<Permission> permissions, Permission permission) {
        if (!permissions.contains(permission)) {
            throw new ForbiddenException(ACCESS_DENIED, "Missing permission " + permission.key(),
                    Map.of("permission", permission.key()));
        }
    }

    /** Персонал курса видит скрытое и получает полные представления. */
    static boolean isStaff(Set<Permission> permissions) {
        return permissions.contains(Permission.COURSE_VIEW_HIDDEN);
    }
}
