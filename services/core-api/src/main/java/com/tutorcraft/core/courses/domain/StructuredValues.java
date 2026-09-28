package com.tutorcraft.core.courses.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Операции над JSON-подобными значениями (Map/List/скаляры) настроек, условий и правил выполнения. */
public final class StructuredValues {

    private static final int UUID_TEXT_LENGTH = 36;

    private StructuredValues() {
    }

    /** Поверхностное слияние настроек: текущие ⊕ patch (ключи patch перекрывают, null — очищает значение). */
    public static Map<String, Object> merge(Map<String, Object> current, Map<String, Object> patch) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (current != null) {
            result.putAll(current);
        }
        if (patch != null) {
            result.putAll(patch);
        }
        return result;
    }

    /**
     * Глубокая копия с заменой идентификаторов: любая строка, равная старому id из mapping, заменяется новым
     * (условия доступа и правила выполнения ссылаются на элементы по id — при дублировании курса ссылки переносятся).
     */
    public static Object remapIds(Object value, Map<UUID, UUID> mapping) {
        return switch (value) {
            case null -> null;
            case Map<?, ?> map -> remapMap(map, mapping);
            case List<?> list -> list.stream().map(element -> remapIds(element, mapping)).toList();
            case String text -> remapString(text, mapping);
            default -> value;
        };
    }

    public static Map<String, Object> remapMap(Map<?, ?> map, Map<UUID, UUID> mapping) {
        if (map == null) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, element) -> result.put(String.valueOf(key), remapIds(element, mapping)));
        return result;
    }

    /** Глубокая копия в изменяемые LinkedHashMap/ArrayList (например, из org.bson.Document). */
    public static Object deepCopy(Object value) {
        return switch (value) {
            case null -> null;
            case Map<?, ?> map -> copyMap(map);
            case List<?> list -> copyList(list);
            default -> value;
        };
    }

    public static Map<String, Object> copyMap(Map<?, ?> map) {
        if (map == null) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, element) -> result.put(String.valueOf(key), deepCopy(element)));
        return result;
    }

    private static List<Object> copyList(List<?> list) {
        List<Object> result = new ArrayList<>(list.size());
        list.forEach(element -> result.add(deepCopy(element)));
        return result;
    }

    private static String remapString(String text, Map<UUID, UUID> mapping) {
        if (text.length() != UUID_TEXT_LENGTH) {
            return text;
        }
        try {
            UUID replacement = mapping.get(UUID.fromString(text));
            return replacement == null ? text : replacement.toString();
        } catch (IllegalArgumentException e) {
            return text;
        }
    }
}
