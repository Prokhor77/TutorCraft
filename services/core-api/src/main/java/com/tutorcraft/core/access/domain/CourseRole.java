package com.tutorcraft.core.access.domain;

import com.tutorcraft.core.shared.domain.ValidationException;
import java.util.Arrays;

/** Роли в контексте курса (хранятся ключом в enrollments.role_key). */
public enum CourseRole {
    TEACHER(SystemRole.TEACHER), ASSISTANT(SystemRole.ASSISTANT), STUDENT(SystemRole.STUDENT),
    OBSERVER(SystemRole.OBSERVER), GUEST(SystemRole.GUEST);

    private final SystemRole systemRole;

    CourseRole(SystemRole systemRole) {
        this.systemRole = systemRole;
    }

    public String key() {
        return systemRole.key();
    }

    public SystemRole systemRole() {
        return systemRole;
    }

    public static CourseRole fromKey(String key) {
        return Arrays.stream(values()).filter(role -> role.key().equals(key)).findFirst()
                .orElseThrow(() -> ValidationException.single("role", "invalid_role", "Unknown course role"));
    }
}
