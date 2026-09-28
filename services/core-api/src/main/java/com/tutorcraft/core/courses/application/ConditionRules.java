package com.tutorcraft.core.courses.application;

import com.tutorcraft.core.courses.domain.CourseItem;
import com.tutorcraft.core.progress.ConditionSchema;
import com.tutorcraft.core.shared.domain.ValidationException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Условия доступа модулей и элементов (FR-PROG-02): схему и нормализацию задаёт модуль progress
 * ({@link ConditionSchema}), здесь — ссылки на элементы только своего курса и без ссылки элемента на самого себя.
 */
@Component
class ConditionRules {

    static final String FIELD = "conditions";
    private static final String NOT_IN_COURSE = "item_not_in_course";
    private static final String SELF_REFERENCE = "self_reference";

    private final ItemRepository items;

    ConditionRules(ItemRepository items) {
        this.items = items;
    }

    /** @return нормализованное дерево; null — условий нет */
    Map<String, Object> forModule(UUID tenantId, UUID courseId, Map<String, Object> raw) {
        Map<String, Object> normalized = ConditionSchema.validate(raw);
        requireItemsOfCourse(tenantId, courseId, ConditionSchema.referencedItemIds(normalized));
        return normalized;
    }

    /** @return нормализованное дерево; null — условий нет */
    Map<String, Object> forItem(CourseItem item, Map<String, Object> raw) {
        Map<String, Object> normalized = ConditionSchema.validate(raw);
        Set<UUID> referenced = ConditionSchema.referencedItemIds(normalized);
        if (referenced.contains(item.id())) {
            throw ValidationException.single(FIELD, SELF_REFERENCE, "An item cannot depend on itself");
        }
        requireItemsOfCourse(item.tenantId(), item.courseId(), referenced);
        return normalized;
    }

    private void requireItemsOfCourse(UUID tenantId, UUID courseId, Set<UUID> itemIds) {
        if (itemIds.isEmpty()) {
            return;
        }
        Map<UUID, CourseItem> found = items.findAll(tenantId, itemIds);
        boolean allInCourse = itemIds.stream()
                .allMatch(id -> found.containsKey(id) && courseId.equals(found.get(id).courseId()));
        if (!allInCourse) {
            throw ValidationException.single(FIELD, NOT_IN_COURSE, "Conditions may reference only items of the same course");
        }
    }
}
