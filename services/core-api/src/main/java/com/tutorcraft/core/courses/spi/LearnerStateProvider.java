package com.tutorcraft.core.courses.spi;

import com.tutorcraft.core.courses.Availability;
import com.tutorcraft.core.courses.CourseRef;
import com.tutorcraft.core.courses.ItemRef;
import com.tutorcraft.core.courses.ModuleRef;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Доступность (условия) и выполнение для студента. Реализует модуль progress.
 * Ключи availability — id модулей и элементов; completion — id элементов → complete|incomplete.
 */
public interface LearnerStateProvider {

    LearnerState stateFor(UUID tenantId, UUID userId, CourseRef course, List<ModuleRef> modules, List<ItemRef> items);

    record LearnerState(Map<UUID, Availability> availability, Map<UUID, String> completion) {
    }
}
