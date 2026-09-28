package com.tutorcraft.core.progress;

import com.tutorcraft.core.progress.domain.Condition;
import com.tutorcraft.core.progress.domain.ConditionGroup;
import com.tutorcraft.core.progress.domain.ConditionParser;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Схема условий доступа (FR-PROG-02) для модулей, сохраняющих условия (courses: PATCH /modules/{id}, /items/{id}).
 * Вызывающий код дополнительно проверяет, что {@link #referencedItemIds} принадлежат тому же курсу.
 */
public final class ConditionSchema {

    private ConditionSchema() {
    }

    /**
     * Валидирует и нормализует дерево условий.
     * @return нормализованное дерево; null — условий нет (null или пустой объект)
     * @throws com.tutorcraft.core.shared.domain.ValidationException поля {@code conditions.*}
     */
    public static Map<String, Object> validate(Map<String, Object> raw) {
        ConditionGroup group = ConditionParser.parse(raw);
        return group == null ? null : ConditionParser.toMap(group);
    }

    /** Элементы, на которые ссылаются условия completion/grade (для проверки принадлежности курсу). */
    public static Set<UUID> referencedItemIds(Map<String, Object> raw) {
        ConditionGroup group = ConditionParser.parse(raw);
        Set<UUID> ids = new LinkedHashSet<>();
        if (group == null) {
            return ids;
        }
        for (Condition condition : group.conditions()) {
            if (condition instanceof Condition.Completion completion) {
                ids.add(completion.itemId());
            } else if (condition instanceof Condition.Grade grade) {
                ids.add(grade.itemId());
            }
        }
        return ids;
    }
}
