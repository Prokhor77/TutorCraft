package com.tutorcraft.core.enrollment.domain;

import com.tutorcraft.core.shared.domain.Validator;
import java.util.List;
import java.util.UUID;

/** Группа участников курса (FR-ENROL-05). */
public record CourseGroup(UUID id, UUID tenantId, UUID courseId, String name, List<UUID> memberIds) {

    public static final int MAX_NAME = 100;

    public CourseGroup {
        memberIds = memberIds == null ? List.of() : List.copyOf(memberIds);
    }

    public static String validName(String name) {
        new Validator().notBlank(name, "name").maxLength(name == null ? null : name.trim(), MAX_NAME, "name").throwIfInvalid();
        return name.trim();
    }
}
