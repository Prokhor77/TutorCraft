package com.tutorcraft.core.courses.domain;

import com.tutorcraft.core.shared.domain.Validator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Структурная проверка правил выполнения элемента и условий доступа (контракт §5: ItemCompletionRule, ConditionGroup).
 * Семантику условий вычисляет модуль progress; здесь — только форма документа, чтобы в хранилище не попал мусор.
 */
public final class ItemRules {

    public static final Map<String, Object> DEFAULT_COMPLETION_RULE = Map.of("mode", "none");
    public static final int MAX_CONDITIONS = 50;

    private static final String MODE = "mode";
    private static final String ON = "on";
    private static final Set<String> MODES = Set.of("none", "manual", "auto");
    private static final Set<String> TRIGGERS = Set.of("viewed", "submitted", "graded", "passed", "posted");
    private static final Set<String> OPERATORS = Set.of("all", "any");
    private static final Set<String> CONDITION_TYPES = Set.of("date", "completion", "grade", "group");
    private static final Set<String> ID_FIELDS = Set.of("itemId", "groupId");

    private ItemRules() {
    }

    /** Нормализованное правило выполнения {mode, on?}; null → mode none. */
    public static Map<String, Object> completionRule(Map<String, Object> rule, String field) {
        if (rule == null) {
            return DEFAULT_COMPLETION_RULE;
        }
        Object mode = rule.get(MODE);
        Object on = rule.get(ON);
        Validator validator = new Validator()
                .check(mode instanceof String value && MODES.contains(value), field + "." + MODE, "invalid", "Unknown completion mode")
                .check(on == null || on instanceof List<?> list && TRIGGERS.containsAll(list), field + "." + ON, "invalid",
                        "Unknown completion trigger");
        validator.throwIfInvalid();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(MODE, mode);
        if (on != null) {
            result.put(ON, List.copyOf((List<?>) on));
        }
        return result;
    }

    /** Проверка формы ConditionGroup {op, showWhenLocked, conditions[]}; null — условий нет. */
    public static Map<String, Object> conditions(Map<String, Object> group, String field) {
        if (group == null) {
            return null;
        }
        Validator validator = new Validator()
                .check(group.get("op") instanceof String op && OPERATORS.contains(op), field + ".op", "invalid", "op must be all or any")
                .check(group.get("showWhenLocked") == null || group.get("showWhenLocked") instanceof Boolean,
                        field + ".showWhenLocked", "invalid", "showWhenLocked must be boolean");
        Object list = group.get("conditions");
        if (list instanceof List<?> conditions && conditions.size() <= MAX_CONDITIONS) {
            for (int i = 0; i < conditions.size(); i++) {
                checkCondition(validator, conditions.get(i), field + ".conditions[" + i + "]");
            }
        } else {
            validator.check(false, field + ".conditions", "invalid", "conditions must be an array of at most " + MAX_CONDITIONS);
        }
        validator.throwIfInvalid();
        return StructuredValues.copyMap(group);
    }

    private static void checkCondition(Validator validator, Object raw, String path) {
        if (!(raw instanceof Map<?, ?> condition)) {
            validator.check(false, path, "invalid", "Condition must be an object");
            return;
        }
        validator.check(condition.get("type") instanceof String type && CONDITION_TYPES.contains(type), path + ".type",
                "invalid", "Unknown condition type");
        ID_FIELDS.stream().filter(condition::containsKey).forEach(key ->
                validator.check(isUuid(condition.get(key)), path + "." + key, "invalid_uuid", "Invalid identifier"));
    }

    private static boolean isUuid(Object value) {
        if (!(value instanceof String text)) {
            return false;
        }
        try {
            UUID.fromString(text);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
