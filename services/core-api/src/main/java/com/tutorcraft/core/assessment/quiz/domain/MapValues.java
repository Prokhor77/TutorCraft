package com.tutorcraft.core.assessment.quiz.domain;

import com.tutorcraft.core.shared.domain.ValidationException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Типизированное чтение полей из JSON-дерева (Map), пришедшего от клиента или из MongoDB.
 * Отсутствующее поле → null; поле неверного типа → ValidationException с кодом {@code invalid_type}.
 */
public final class MapValues {

    private static final String INVALID_TYPE = "invalid_type";

    private MapValues() {
    }

    public static String string(Map<String, ?> map, String key, String field) {
        Object value = map == null ? null : map.get(key);
        if (value == null || value instanceof String) {
            return (String) value;
        }
        throw invalid(field, "Expected a string");
    }

    public static Boolean bool(Map<String, ?> map, String key, String field) {
        Object value = map == null ? null : map.get(key);
        if (value == null || value instanceof Boolean) {
            return (Boolean) value;
        }
        throw invalid(field, "Expected a boolean");
    }

    public static boolean bool(Map<String, ?> map, String key, String field, boolean fallback) {
        Boolean value = bool(map, key, field);
        return value == null ? fallback : value;
    }

    public static Double number(Map<String, ?> map, String key, String field) {
        Object value = map == null ? null : map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number && Double.isFinite(number.doubleValue())) {
            return number.doubleValue();
        }
        throw invalid(field, "Expected a finite number");
    }

    public static Integer integer(Map<String, ?> map, String key, String field) {
        Double value = number(map, key, field);
        if (value == null) {
            return null;
        }
        if (value != Math.rint(value) || Math.abs(value) > Integer.MAX_VALUE) {
            throw invalid(field, "Expected an integer");
        }
        return value.intValue();
    }

    public static UUID uuid(Map<String, ?> map, String key, String field) {
        Object value = map == null ? null : map.get(key);
        if (value == null || value instanceof UUID) {
            return (UUID) value;
        }
        if (value instanceof String text) {
            return parseUuid(text, field);
        }
        throw invalid(field, "Expected an identifier");
    }

    public static Instant instant(Map<String, ?> map, String key, String field) {
        Object value = map == null ? null : map.get(key);
        return switch (value) {
            case null -> null;
            case Instant instant -> instant;
            case Date date -> date.toInstant();
            case String text -> parseInstant(text, field);
            default -> throw invalid(field, "Expected an ISO-8601 instant");
        };
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Map<String, ?> map, String key, String field) {
        Object value = map == null ? null : map.get(key);
        if (value == null || value instanceof Map<?, ?>) {
            return (Map<String, Object>) value;
        }
        throw invalid(field, "Expected an object");
    }

    public static List<?> list(Map<String, ?> map, String key, String field) {
        Object value = map == null ? null : map.get(key);
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list;
        }
        throw invalid(field, "Expected an array");
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObject(Object element, String field) {
        if (element instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw invalid(field, "Expected an object");
    }

    public static String asString(Object element, String field) {
        if (element instanceof String text) {
            return text;
        }
        throw invalid(field, "Expected a string");
    }

    public static UUID parseUuid(String text, String field) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            throw ValidationException.single(field, "invalid_uuid", "Invalid identifier");
        }
    }

    private static Instant parseInstant(String text, String field) {
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException e) {
            throw ValidationException.single(field, "invalid_instant", "Expected an ISO-8601 instant");
        }
    }

    private static ValidationException invalid(String field, String message) {
        return ValidationException.single(field, INVALID_TYPE, message);
    }
}
