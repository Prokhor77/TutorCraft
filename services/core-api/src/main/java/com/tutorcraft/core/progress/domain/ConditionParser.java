package com.tutorcraft.core.progress.domain;

import com.tutorcraft.core.progress.domain.ConditionGroup.Operator;
import com.tutorcraft.core.shared.domain.ValidationException;
import com.tutorcraft.core.shared.domain.Validator;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Разбор и валидация JSON-дерева условий (контракт §5 ConditionGroup) и обратная сериализация
 * в нормализованный вид для хранения. Поля ошибок — {@code conditions.*}.
 */
public final class ConditionParser {

    public static final int MAX_CONDITIONS = 20;
    static final double MAX_PERCENT = 100;
    private static final String FIELD = "conditions";
    private static final String TYPE = "type";
    private static final String ITEM_ID = "itemId";

    private ConditionParser() {
    }

    /** @return null для null/пустого ввода — «без условий» */
    public static ConditionGroup parse(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        Operator operator = operator(raw.get("op"));
        boolean showWhenLocked = !(raw.get("showWhenLocked") instanceof Boolean flag) || flag;
        List<?> rawList = raw.get(FIELD) instanceof List<?> list ? list : null;
        if (rawList == null) {
            throw ValidationException.single(FIELD + ".conditions", "required", "Conditions list is required");
        }
        Validator validator = new Validator();
        validator.check(rawList.size() <= MAX_CONDITIONS, FIELD + ".conditions", "too_many",
                "At most " + MAX_CONDITIONS + " conditions are allowed");
        List<Condition> conditions = new ArrayList<>();
        for (int i = 0; i < Math.min(rawList.size(), MAX_CONDITIONS); i++) {
            conditions.add(condition(rawList.get(i), FIELD + ".conditions[" + i + "]", validator));
        }
        validator.throwIfInvalid();
        return new ConditionGroup(operator, showWhenLocked, conditions);
    }

    public static Map<String, Object> toMap(ConditionGroup group) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("op", group.operator().key());
        map.put("showWhenLocked", group.showWhenLocked());
        map.put(FIELD, group.conditions().stream().map(ConditionParser::toMap).toList());
        return map;
    }

    private static Map<String, Object> toMap(Condition condition) {
        Map<String, Object> map = new LinkedHashMap<>();
        switch (condition) {
            case Condition.DateWindow window -> {
                map.put(TYPE, "date");
                map.put("from", window.from() == null ? null : window.from().toString());
                map.put("until", window.until() == null ? null : window.until().toString());
            }
            case Condition.Completion completion -> {
                map.put(TYPE, "completion");
                map.put(ITEM_ID, completion.itemId().toString());
                map.put("state", completion.requireComplete() ? "complete" : "incomplete");
            }
            case Condition.Grade grade -> {
                map.put(TYPE, "grade");
                map.put(ITEM_ID, grade.itemId().toString());
                map.put("minPercent", grade.minPercent());
                map.put("maxPercent", grade.maxPercent());
            }
            case Condition.Group group -> {
                map.put(TYPE, "group");
                map.put("groupId", group.groupId().toString());
            }
        }
        return map;
    }

    private static Operator operator(Object raw) {
        if (raw == null || "all".equals(raw)) {
            return Operator.ALL;
        }
        if ("any".equals(raw)) {
            return Operator.ANY;
        }
        throw ValidationException.single(FIELD + ".op", "invalid", "op must be 'all' or 'any'");
    }

    @SuppressWarnings("unchecked")
    private static Condition condition(Object raw, String field, Validator validator) {
        if (!(raw instanceof Map<?, ?> map)) {
            throw ValidationException.single(field, "invalid_type", "Expected an object");
        }
        Map<String, Object> entry = (Map<String, Object>) map;
        Object type = entry.get(TYPE);
        return switch (type instanceof String key ? key : "") {
            case "date" -> date(entry, field, validator);
            case "completion" -> completion(entry, field);
            case "grade" -> grade(entry, field, validator);
            case "group" -> new Condition.Group(uuid(entry.get("groupId"), field + ".groupId"));
            default -> throw ValidationException.single(field + ".type", "invalid", "Unknown condition type");
        };
    }

    private static Condition date(Map<String, Object> entry, String field, Validator validator) {
        Instant from = instant(entry.get("from"), field + ".from");
        Instant until = instant(entry.get("until"), field + ".until");
        validator.check(from != null || until != null, field, "bound_required", "from or until is required")
            .check(from == null || until == null || from.isBefore(until), field + ".until", "before_from",
                    "until must be after from");
        return new Condition.DateWindow(from, until);
    }

    private static Condition completion(Map<String, Object> entry, String field) {
        Object state = entry.get("state");
        if (state != null && !"complete".equals(state) && !"incomplete".equals(state)) {
            throw ValidationException.single(field + ".state", "invalid", "state must be 'complete' or 'incomplete'");
        }
        return new Condition.Completion(uuid(entry.get(ITEM_ID), field + "." + ITEM_ID), !"incomplete".equals(state));
    }

    private static Condition grade(Map<String, Object> entry, String field, Validator validator) {
        Double min = percent(entry.get("minPercent"), field + ".minPercent", validator);
        Double max = percent(entry.get("maxPercent"), field + ".maxPercent", validator);
        validator.check(min != null || max != null, field, "bound_required", "minPercent or maxPercent is required")
            .check(min == null || max == null || min < max, field + ".maxPercent", "not_greater",
                    "maxPercent must be greater than minPercent");
        return new Condition.Grade(uuid(entry.get(ITEM_ID), field + "." + ITEM_ID), min, max);
    }

    private static Double percent(Object raw, String field, Validator validator) {
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw ValidationException.single(field, "invalid_type", "Expected a number");
        }
        double value = number.doubleValue();
        validator.check(value >= 0 && value <= MAX_PERCENT, field, "out_of_range", "Must be between 0 and 100");
        return value;
    }

    private static UUID uuid(Object raw, String field) {
        if (raw instanceof UUID id) {
            return id;
        }
        if (!(raw instanceof String text)) {
            throw ValidationException.single(field, "invalid_uuid", "A valid identifier is required");
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            throw ValidationException.single(field, "invalid_uuid", "A valid identifier is required");
        }
    }

    private static Instant instant(Object raw, String field) {
        return switch (raw) {
            case null -> null;
            case Instant value -> value;
            case Date date -> date.toInstant();
            case String text -> parseInstant(text, field);
            default -> throw ValidationException.single(field, "invalid_type", "Expected an ISO-8601 instant");
        };
    }

    private static Instant parseInstant(String text, String field) {
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException e) {
            throw ValidationException.single(field, "invalid_instant", "Expected an ISO-8601 instant");
        }
    }
}
