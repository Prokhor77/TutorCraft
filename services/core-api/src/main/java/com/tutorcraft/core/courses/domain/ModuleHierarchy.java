package com.tutorcraft.core.courses.domain;

import com.tutorcraft.core.shared.domain.BusinessRuleException;
import java.util.UUID;

/** Модули допускают один уровень вложенности (FR-COURSE-03, ТЗ 4). */
public final class ModuleHierarchy {

    public static final String DEPTH_EXCEEDED = "module.depth_exceeded";

    private ModuleHierarchy() {
    }

    /**
     * Проверяет, что модуль (moduleId == null — новый) можно поместить под parent (null — верхний уровень).
     * @param moduleHasChildren у перемещаемого модуля есть подмодули
     */
    public static void requireValidParent(UUID moduleId, CourseModule parent, boolean moduleHasChildren) {
        if (parent == null) {
            return;
        }
        if (parent.id().equals(moduleId) || !parent.isTopLevel() || moduleHasChildren) {
            throw new BusinessRuleException(DEPTH_EXCEEDED, "Modules support only one level of nesting");
        }
    }
}
