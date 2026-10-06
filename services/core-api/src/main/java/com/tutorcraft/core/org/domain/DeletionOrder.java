package com.tutorcraft.core.org.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Порядок удаления таблиц по внешним ключам: строки, которые ссылаются на другие, удаляются раньше тех, на которые
 * ссылаются. Ссылка таблицы на саму себя порядок не задаёт (все строки tenant удаляются одним оператором, а FK
 * без DEFERRABLE проверяется в конце оператора). Среди равноправных таблиц порядок алфавитный — детерминирован.
 */
public final class DeletionOrder {

    private DeletionOrder() {
    }

    /**
     * @param tables     удаляемые таблицы
     * @param references ссылки между ними (ссылки на таблицы вне {@code tables} игнорируются)
     * @throws IllegalStateException цикл ссылок между разными таблицами
     */
    public static List<String> of(Collection<String> tables, Collection<Reference> references) {
        Set<String> pending = new TreeSet<>(tables);
        Map<String, Set<String>> referencedBy = new HashMap<>();
        for (Reference reference : references) {
            boolean internal = pending.contains(reference.child()) && pending.contains(reference.parent());
            if (internal && !reference.child().equals(reference.parent())) {
                referencedBy.computeIfAbsent(reference.parent(), key -> new LinkedHashSet<>()).add(reference.child());
            }
        }
        List<String> order = new ArrayList<>(pending.size());
        while (!pending.isEmpty()) {
            String next = pending.stream()
                    .filter(table -> referencedBy.getOrDefault(table, Set.of()).stream().noneMatch(pending::contains))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Foreign key cycle between tables " + pending));
            order.add(next);
            pending.remove(next);
        }
        return List.copyOf(order);
    }

    /** {@code child} ссылается на {@code parent}. */
    public record Reference(String child, String parent) {
    }
}
