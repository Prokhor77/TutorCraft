package com.tutorcraft.core.activity.domain;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Параметры пути для журнала. Идентификаторы объектов ({@code courseId}) полезны для расследования и остаются,
 * секреты в пути ({@code /calendar/ical/{token}}, ссылки-приглашения) маскируются по имени переменной.
 */
public final class PathParams {

    private static final Set<String> SENSITIVE_MARKERS = Set.of("token", "secret", "code", "key", "password", "hash");
    private static final int MAX_VALUE_LENGTH = 100;

    private PathParams() {
    }

    public static Map<String, String> sanitize(Map<String, String> variables) {
        if (variables == null || variables.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        variables.forEach((name, value) -> result.put(name, sanitizeValue(name, value)));
        return result;
    }

    /** Фактический путь, в котором значения секретных переменных заменены маской. */
    public static String maskPath(String path, Map<String, String> variables) {
        if (path == null || variables == null) {
            return path;
        }
        String result = path;
        for (Map.Entry<String, String> variable : variables.entrySet()) {
            String value = variable.getValue();
            if (isSensitive(variable.getKey()) && value != null && !value.isEmpty()) {
                result = result.replace(value, SensitiveDataMasker.MASK);
            }
        }
        return result;
    }

    static boolean isSensitive(String variableName) {
        String lower = variableName.toLowerCase(Locale.ROOT);
        return SENSITIVE_MARKERS.stream().anyMatch(lower::contains);
    }

    private static String sanitizeValue(String name, String value) {
        if (isSensitive(name)) {
            return SensitiveDataMasker.MASK;
        }
        return SensitiveDataMasker.truncate(value, MAX_VALUE_LENGTH);
    }
}
