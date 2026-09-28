package com.tutorcraft.core.assessment.assignment.domain;

import com.tutorcraft.core.shared.domain.FieldViolation;
import com.tutorcraft.core.shared.domain.ValidationException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Типизированное чтение настроек из JSON-подобной Map (запрос API или документ MongoDB) с накоплением
 * нарушений по полям {@code settings.<name>}. Отсутствующее значение или null → умолчание.
 */
final class SettingsReader {

    private static final String FIELD_PREFIX = "settings.";
    private static final String INVALID = "invalid";
    private static final String OUT_OF_RANGE = "out_of_range";

    private final Map<String, Object> source;
    private final List<FieldViolation> violations = new ArrayList<>();

    SettingsReader(Map<String, Object> source) {
        this.source = source == null ? Map.of() : source;
    }

    String string(String key, String fallback) {
        Object value = source.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof String text) {
            return text;
        }
        return reject(key, INVALID, "Must be a string", fallback);
    }

    boolean bool(String key, boolean fallback) {
        Object value = source.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean flag) {
            return flag;
        }
        return reject(key, INVALID, "Must be a boolean", fallback);
    }

    int integer(String key, int fallback, int min, int max) {
        Integer value = optionalInteger(key, min, max);
        return value == null ? fallback : value;
    }

    Integer optionalInteger(String key, int min, int max) {
        BigDecimal value = decimalValue(key);
        if (value == null) {
            return null;
        }
        if (value.stripTrailingZeros().scale() > 0) {
            return reject(key, INVALID, "Must be an integer", null);
        }
        if (!inRange(value, BigDecimal.valueOf(min), BigDecimal.valueOf(max))) {
            return reject(key, OUT_OF_RANGE, "Must be between " + min + " and " + max, null);
        }
        return Integer.valueOf(value.intValue());
    }

    BigDecimal decimal(String key, BigDecimal fallback, BigDecimal min, BigDecimal max) {
        BigDecimal value = decimalValue(key);
        if (value == null) {
            return fallback;
        }
        return inRange(value, min, max) ? value
                : reject(key, OUT_OF_RANGE, "Must be between " + min.toPlainString() + " and " + max.toPlainString(), fallback);
    }

    Instant instant(String key) {
        Object value = source.get(key);
        return switch (value) {
            case null -> null;
            case Instant instant -> instant;
            case Date date -> date.toInstant();
            case String text -> parseInstant(key, text);
            default -> reject(key, INVALID, "Must be an ISO-8601 instant", null);
        };
    }

    UUID uuid(String key) {
        Object value = source.get(key);
        return switch (value) {
            case null -> null;
            case UUID id -> id;
            case String text -> parseUuid(key, text);
            default -> reject(key, INVALID, "Must be an identifier", null);
        };
    }

    List<String> strings(String key) {
        Object value = source.get(key);
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list && list.stream().allMatch(String.class::isInstance)) {
            return list.stream().map(String.class::cast).toList();
        }
        return reject(key, INVALID, "Must be a list of strings", List.of());
    }

    /** Добавляет нарушение правила, затрагивающего поле. */
    void violation(String key, String code, String message) {
        violations.add(new FieldViolation(FIELD_PREFIX + key, code, message));
    }

    void throwIfInvalid() {
        if (!violations.isEmpty()) {
            throw new ValidationException(violations);
        }
    }

    private BigDecimal decimalValue(String key) {
        Object value = source.get(key);
        return switch (value) {
            case null -> null;
            case BigDecimal decimal -> decimal;
            case Integer number -> BigDecimal.valueOf(number);
            case Long number -> BigDecimal.valueOf(number);
            case Number number -> finiteDecimal(key, number.doubleValue());
            case String text -> parseDecimal(key, text);
            default -> reject(key, INVALID, "Must be a number", null);
        };
    }

    private BigDecimal finiteDecimal(String key, double value) {
        return Double.isFinite(value) ? BigDecimal.valueOf(value) : reject(key, INVALID, "Must be a number", null);
    }

    private BigDecimal parseDecimal(String key, String text) {
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            return reject(key, INVALID, "Must be a number", null);
        }
    }

    private Instant parseInstant(String key, String text) {
        try {
            return Instant.parse(text.trim());
        } catch (DateTimeParseException e) {
            return reject(key, INVALID, "Must be an ISO-8601 instant", null);
        }
    }

    private UUID parseUuid(String key, String text) {
        try {
            return UUID.fromString(text.trim());
        } catch (IllegalArgumentException e) {
            return reject(key, INVALID, "Must be an identifier", null);
        }
    }

    private static boolean inRange(BigDecimal value, BigDecimal min, BigDecimal max) {
        return value.compareTo(min) >= 0 && value.compareTo(max) <= 0;
    }

    private <T> T reject(String key, String code, String message, T fallback) {
        violation(key, code, message);
        return fallback;
    }
}
