package com.tutorcraft.core.courses.application;

import com.tutorcraft.core.courses.Availability;
import com.tutorcraft.core.courses.application.OutlineView.ModuleView;
import com.tutorcraft.core.courses.application.OutlineView.OutlineItemView;
import com.tutorcraft.core.courses.domain.AvailabilityRules;
import com.tutorcraft.core.courses.domain.CourseItem;
import com.tutorcraft.core.courses.domain.CourseModule;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Дерево оглавления из снимка: модули верхнего уровня → подмодули → элементы, по позиции.
 * Недоступное в режиме «скрыть» не попадает в ответ учащемуся (FR-PROG-03); иначе показывается с причинами (UX-07).
 */
@Component
class OutlineAssembler {

    private final ItemViews itemViews;

    OutlineAssembler(ItemViews itemViews) {
        this.itemViews = itemViews;
    }

    OutlineView outline(UUID courseId, LearnerSnapshot snapshot) {
        Structure structure = new Structure(snapshot);
        List<ModuleView> modules = structure.childrenOf(null).stream()
                .map(module -> module(structure, module, Availability.open()))
                .filter(Objects::nonNull)
                .toList();
        return new OutlineView(courseId, modules);
    }

    /** Один модуль (с подмодулями и элементами) — ответ на создание/правку модуля. */
    Optional<ModuleView> module(LearnerSnapshot snapshot, UUID moduleId) {
        Structure structure = new Structure(snapshot);
        return snapshot.modules().stream()
                .filter(module -> module.id().equals(moduleId))
                .findFirst()
                .map(module -> module(structure, module, Availability.open()));
    }

    private ModuleView module(Structure structure, CourseModule module, Availability outer) {
        Availability availability = AvailabilityRules.combine(outer, structure.snapshot.availabilityOf(module.id()));
        if (AvailabilityRules.hiddenFromLearner(availability)) {
            return null;
        }
        List<OutlineItemView> items = structure.itemsOf(module.id()).stream()
                .map(item -> item(structure.snapshot, item, availability))
                .filter(Objects::nonNull)
                .toList();
        List<ModuleView> children = structure.childrenOf(module.id()).stream()
                .map(child -> module(structure, child, availability))
                .filter(Objects::nonNull)
                .toList();
        return new ModuleView(module.id(), module.parentId(), module.title(), module.position(), module.visibility().key(),
                module.publishAt(), availability, items, children, module.version());
    }

    private OutlineItemView item(LearnerSnapshot snapshot, CourseItem item, Availability moduleAvailability) {
        Availability availability = AvailabilityRules.combine(moduleAvailability, snapshot.availabilityOf(item.id()));
        if (AvailabilityRules.hiddenFromLearner(availability)) {
            return null;
        }
        return itemViews.outline(item, availability, snapshot.completionOf(item.id()), snapshot.statusOf(item.id()));
    }

    /** Индексы снимка по родителю. */
    private static final class Structure {

        private static final UUID ROOT = new UUID(0L, 0L);

        private final LearnerSnapshot snapshot;
        private final Map<UUID, List<CourseModule>> modulesByParent;
        private final Map<UUID, List<CourseItem>> itemsByModule;

        Structure(LearnerSnapshot snapshot) {
            this.snapshot = snapshot;
            this.modulesByParent = snapshot.modules().stream()
                    .sorted(Comparator.comparingInt(CourseModule::position))
                    .collect(Collectors.groupingBy(module -> module.isTopLevel() ? ROOT : module.parentId()));
            this.itemsByModule = snapshot.items().stream()
                    .sorted(Comparator.comparingInt(CourseItem::position))
                    .collect(Collectors.groupingBy(CourseItem::moduleId));
        }

        List<CourseModule> childrenOf(UUID parentId) {
            return modulesByParent.getOrDefault(parentId == null ? ROOT : parentId, List.of());
        }

        List<CourseItem> itemsOf(UUID moduleId) {
            return itemsByModule.getOrDefault(moduleId, List.of());
        }
    }
}
