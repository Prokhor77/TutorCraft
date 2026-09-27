package com.tutorcraft.core.access.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Контекст проверки прав (ТЗ 3.1). Модуль/элемент курса проверяются в контексте их курса —
 * вызывающий код передаёт {@code course(courseId)}.
 */
public record AccessContext(Type type, UUID id) {

    public enum Type { TENANT, CATEGORY, COURSE }

    public AccessContext {
        Objects.requireNonNull(type, "type");
        if (type != Type.TENANT) {
            Objects.requireNonNull(id, "id is required for " + type);
        }
    }

    public static AccessContext tenant() {
        return new AccessContext(Type.TENANT, null);
    }

    public static AccessContext category(UUID categoryId) {
        return new AccessContext(Type.CATEGORY, categoryId);
    }

    public static AccessContext course(UUID courseId) {
        return new AccessContext(Type.COURSE, courseId);
    }
}
