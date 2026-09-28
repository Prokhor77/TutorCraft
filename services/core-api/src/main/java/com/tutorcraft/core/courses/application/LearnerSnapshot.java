package com.tutorcraft.core.courses.application;

import com.tutorcraft.core.courses.Availability;
import com.tutorcraft.core.courses.domain.AvailabilityRules;
import com.tutorcraft.core.courses.domain.CourseItem;
import com.tutorcraft.core.courses.domain.CourseModule;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Структура курса глазами пользователя: видимые модули/элементы, доступность, выполнение и статусы.
 * Для персонала (course.viewHidden) — всё, доступно, без выполнения/статусов.
 */
final class LearnerSnapshot {

    private final List<CourseModule> modules;
    private final List<CourseItem> items;
    private final Map<UUID, CourseModule> modulesById;
    private final Map<UUID, Availability> availability;
    private final Map<UUID, String> completion;
    private final Map<UUID, String> statuses;

    private LearnerSnapshot(List<CourseModule> modules, List<CourseItem> items, Map<UUID, Availability> availability,
                            Map<UUID, String> completion, Map<UUID, String> statuses) {
        this.modules = List.copyOf(modules);
        this.items = List.copyOf(items);
        this.modulesById = modules.stream().collect(Collectors.toMap(CourseModule::id, Function.identity()));
        this.availability = copy(availability);
        this.completion = copy(completion);
        this.statuses = copy(statuses);
    }

    /** Копия, допускающая null-значения от внешних провайдеров. */
    private static <V> Map<UUID, V> copy(Map<UUID, V> source) {
        return source == null ? Map.of() : Collections.unmodifiableMap(new HashMap<>(source));
    }

    static LearnerSnapshot staff(List<CourseModule> modules, List<CourseItem> items) {
        return new LearnerSnapshot(modules, items, Map.of(), Map.of(), Map.of());
    }

    static LearnerSnapshot learner(List<CourseModule> modules, List<CourseItem> items, Map<UUID, Availability> availability,
                                   Map<UUID, String> completion, Map<UUID, String> statuses) {
        return new LearnerSnapshot(modules, items, availability, completion, statuses);
    }

    List<CourseModule> modules() {
        return modules;
    }

    List<CourseItem> items() {
        return items;
    }

    boolean contains(CourseItem item) {
        return items.stream().anyMatch(candidate -> candidate.id().equals(item.id()));
    }

    Availability availabilityOf(UUID id) {
        return availability.getOrDefault(id, Availability.open());
    }

    String completionOf(UUID itemId) {
        return completion.get(itemId);
    }

    String statusOf(UUID itemId) {
        return statuses.get(itemId);
    }

    /** Доступность элемента с учётом модуля и родительского модуля. */
    Availability effectiveAvailability(CourseItem item) {
        CourseModule module = modulesById.get(item.moduleId());
        Availability moduleAvailability = module == null ? Availability.open() : effectiveModuleAvailability(module);
        return AvailabilityRules.combine(moduleAvailability, availabilityOf(item.id()));
    }

    private Availability effectiveModuleAvailability(CourseModule module) {
        Availability own = availabilityOf(module.id());
        if (module.isTopLevel()) {
            return own;
        }
        return AvailabilityRules.combine(availabilityOf(module.parentId()), own);
    }
}
