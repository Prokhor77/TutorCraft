package com.tutorcraft.core.courses.domain;

import com.tutorcraft.core.shared.domain.Validator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Структурная проверка правила выполнения элемента (контракт §5: ItemCompletionRule).
 * Условия доступа (ConditionGroup) проверяет модуль progress — {@code progress.ConditionSchema} (см. ConditionRules).
 */
public final class ItemRules {

    public static final Map<String, Object> DEFAULT_COMPLETION_RULE = Map.of("mode", "none");

    private static final String MODE = "mode";
    private static final String ON = "on";
    private static final Set<String> MODES = Set.of("none", "manual", "auto");
    private static final Set<String> TRIGGERS = Set.of("viewed", "submitted", "graded", "passed", "posted");

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
}
