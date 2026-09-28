package com.tutorcraft.core.courses.application.activity;

import com.tutorcraft.core.shared.domain.Validator;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Чтение и проверка полей настроек материалов; пути ошибок — {@code settings.<name>}. */
final class SettingsFields {

    static final String KIND = "kind";
    static final String FILE_ID = "fileId";
    private static final String PREFIX = "settings.";

    private SettingsFields() {
    }

    static String path(String name) {
        return PREFIX + name;
    }

    /** UUID-строка или null; некорректное значение → нарушение invalid_uuid. */
    static String optionalUuid(Map<String, Object> settings, String name, Validator validator) {
        Object raw = settings.get(name);
        if (raw == null) {
            return null;
        }
        Optional<UUID> parsed = parse(raw);
        validator.check(parsed.isPresent(), path(name), "invalid_uuid", "Invalid identifier");
        return parsed.map(UUID::toString).orElse(null);
    }

    /** Список UUID без повторов, не длиннее max. */
    static List<String> uuidList(Map<String, Object> settings, String name, int max, Validator validator) {
        Object raw = settings.get(name);
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> list) || list.size() > max) {
            validator.check(false, path(name), "invalid", "Expected a list of at most " + max + " identifiers");
            return List.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (Object element : list) {
            Optional<UUID> parsed = parse(element);
            validator.check(parsed.isPresent(), path(name), "invalid_uuid", "Invalid identifier");
            parsed.ifPresent(id -> result.add(id.toString()));
        }
        return new ArrayList<>(result);
    }

    /** Строка (обрезанная) или null; не строка → invalid. */
    static String optionalString(Map<String, Object> settings, String name, int maxLength, Validator validator) {
        Object raw = settings.get(name);
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof String text)) {
            validator.check(false, path(name), "invalid", "Expected a string");
            return null;
        }
        String trimmed = text.trim();
        validator.maxLength(trimmed, maxLength, path(name));
        return trimmed.isEmpty() ? null : trimmed;
    }

    static Set<UUID> toUuids(Object value) {
        Set<UUID> result = new LinkedHashSet<>();
        if (value instanceof List<?> list) {
            list.forEach(element -> parse(element).ifPresent(result::add));
        } else {
            parse(value).ifPresent(result::add);
        }
        return result;
    }

    private static Optional<UUID> parse(Object raw) {
        if (!(raw instanceof String text)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(text));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
