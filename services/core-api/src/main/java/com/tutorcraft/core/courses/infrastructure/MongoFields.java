package com.tutorcraft.core.courses.infrastructure;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.bson.Document;
import org.bson.types.Decimal128;

/** Имена полей и конвертация значений документов modules/items (даты — BSON date с точностью до мс). */
final class MongoFields {

    static final String ID = "_id";
    static final String TENANT_ID = "tenantId";
    static final String COURSE_ID = "courseId";
    static final String MODULE_ID = "moduleId";
    static final String PARENT_ID = "parentId";
    static final String TYPE = "type";
    static final String TITLE = "title";
    static final String POSITION = "position";
    static final String VISIBILITY = "visibility";
    static final String PUBLISH_AT = "publishAt";
    static final String SETTINGS = "settings";
    static final String CONTENT = "content";
    static final String COMPLETION_RULE = "completionRule";
    static final String CONDITIONS = "conditions";
    static final String DUE_AT = "dueAt";
    static final String OPEN_AT = "openAt";
    static final String CLOSE_AT = "closeAt";
    static final String VERSION = "version";
    static final String DELETED_AT = "deletedAt";
    static final String CREATED_AT = "createdAt";
    static final String UPDATED_AT = "updatedAt";

    private MongoFields() {
    }

    static Date date(Instant instant) {
        return instant == null ? null : Date.from(instant);
    }

    static Instant instant(Document document, String field) {
        Date value = document.getDate(field);
        return value == null ? null : value.toInstant();
    }

    /**
     * Вложенный документ как обычная изменяемая Map без BSON-типов снаружи слоя:
     * org.bson.Document → LinkedHashMap, Decimal128 (так хранится BigDecimal) → BigDecimal.
     */
    static Map<String, Object> map(Document document, String field) {
        Object value = document.get(field);
        return value instanceof Map<?, ?> map ? plainMap(map) : null;
    }

    private static Map<String, Object> plainMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, element) -> result.put(String.valueOf(key), plain(element)));
        return result;
    }

    private static Object plain(Object value) {
        return switch (value) {
            case null -> null;
            case Map<?, ?> nested -> plainMap(nested);
            case List<?> list -> list.stream().map(MongoFields::plain).collect(Collectors.toCollection(ArrayList::new));
            case Decimal128 decimal -> decimal.bigDecimalValue();
            default -> value;
        };
    }

    static long version(Document document) {
        Number value = document.get(VERSION, Number.class);
        return value == null ? 0L : value.longValue();
    }

    static int position(Document document) {
        Number value = document.get(POSITION, Number.class);
        return value == null ? 0 : value.intValue();
    }
}
