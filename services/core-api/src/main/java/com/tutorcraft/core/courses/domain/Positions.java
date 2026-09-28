package com.tutorcraft.core.courses.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Порядок элементов/модулей (drag&amp;drop, FR-COURSE-03): позиции — плотные индексы 0..n-1.
 * Операции возвращают новый порядок; {@link #changes} — только изменившиеся позиции для записи в хранилище.
 */
public final class Positions {

    private Positions() {
    }

    /** Позиция, обрезанная в [0, size]. */
    public static int clamp(int position, int size) {
        return Math.max(0, Math.min(position, size));
    }

    public static List<UUID> remove(List<UUID> ordered, UUID id) {
        List<UUID> result = new ArrayList<>(ordered);
        result.remove(id);
        return result;
    }

    /** Вставка id на позицию (с обрезкой); если id уже в списке — сначала удаляется (перемещение внутри списка). */
    public static List<UUID> insert(List<UUID> ordered, UUID id, int position) {
        List<UUID> result = remove(ordered, id);
        result.add(clamp(position, result.size()), id);
        return result;
    }

    /** id → новая позиция для тех, у кого позиция изменилась относительно current. */
    public static Map<UUID, Integer> changes(List<UUID> newOrder, Map<UUID, Integer> current) {
        Map<UUID, Integer> result = new LinkedHashMap<>();
        for (int index = 0; index < newOrder.size(); index++) {
            UUID id = newOrder.get(index);
            Integer before = current.get(id);
            if (before == null || before != index) {
                result.put(id, index);
            }
        }
        return result;
    }
}
