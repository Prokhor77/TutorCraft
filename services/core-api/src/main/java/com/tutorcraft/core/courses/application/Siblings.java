package com.tutorcraft.core.courses.application;

import com.tutorcraft.core.courses.domain.CourseItem;
import com.tutorcraft.core.courses.domain.CourseModule;
import com.tutorcraft.core.courses.domain.Positions;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/** Упорядоченные «соседи» (модули одного родителя, элементы одного модуля) и расчёт изменений позиций. */
final class Siblings {

    private final List<UUID> orderedIds;
    private final Map<UUID, Integer> positions;

    private Siblings(List<UUID> orderedIds, Map<UUID, Integer> positions) {
        this.orderedIds = orderedIds;
        this.positions = positions;
    }

    static Siblings ofModules(List<CourseModule> all, UUID parentId) {
        List<CourseModule> siblings = all.stream()
                .filter(module -> Objects.equals(module.parentId(), parentId))
                .sorted(Comparator.comparingInt(CourseModule::position).thenComparing(CourseModule::id))
                .toList();
        return new Siblings(siblings.stream().map(CourseModule::id).toList(),
                siblings.stream().collect(Collectors.toMap(CourseModule::id, CourseModule::position)));
    }

    static Siblings ofItems(List<CourseItem> itemsOfModule) {
        List<CourseItem> sorted = itemsOfModule.stream()
                .sorted(Comparator.comparingInt(CourseItem::position).thenComparing(CourseItem::id))
                .toList();
        return new Siblings(sorted.stream().map(CourseItem::id).toList(),
                sorted.stream().collect(Collectors.toMap(CourseItem::id, CourseItem::position)));
    }

    int size() {
        return orderedIds.size();
    }

    /** Новый порядок после вставки (или перемещения) id на позицию. */
    List<UUID> withInserted(UUID id, int position) {
        return Positions.insert(orderedIds, id, position);
    }

    List<UUID> without(UUID id) {
        return Positions.remove(orderedIds, id);
    }

    /** Изменившиеся позиции существующих соседей для нового порядка (кроме excludedId — он записывается отдельно). */
    Map<UUID, Integer> changes(List<UUID> newOrder, UUID excludedId) {
        Map<UUID, Integer> changes = Positions.changes(newOrder, positions);
        changes.remove(excludedId);
        return changes;
    }

    int positionOf(List<UUID> order, UUID id) {
        return order.indexOf(id);
    }
}
