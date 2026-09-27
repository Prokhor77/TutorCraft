package com.tutorcraft.core.org.domain;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Проверки дерева категорий (запрет циклов при перемещении). */
public final class CategoryTree {

    private final Map<UUID, Category> byId;

    public CategoryTree(Collection<Category> categories) {
        this.byId = categories.stream().collect(Collectors.toMap(Category::id, Function.identity()));
    }

    /** true, если перенос {@code categoryId} под {@code newParentId} создаст цикл. */
    public boolean wouldCreateCycle(UUID categoryId, UUID newParentId) {
        UUID cursor = newParentId;
        while (cursor != null) {
            if (Objects.equals(cursor, categoryId)) {
                return true;
            }
            Category current = byId.get(cursor);
            cursor = current == null ? null : current.parentId();
        }
        return false;
    }
}
