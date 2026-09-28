package com.tutorcraft.core.courses.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutorcraft.core.courses.domain.Patch;
import com.tutorcraft.core.shared.domain.ValidationException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Тело PATCH как JSON-дерево: поле отсутствует → {@link Patch#absent()}, передано (в т.ч. null) → {@link Patch#of}.
 * Ошибки преобразования → 400 с путём поля.
 */
final class PatchBody {

    static final String VERSION = "version";
    private static final TypeReference<Map<String, Object>> OBJECT = new TypeReference<>() {
    };

    private final JsonNode node;
    private final ObjectMapper mapper;

    private PatchBody(JsonNode node, ObjectMapper mapper) {
        this.node = node;
        this.mapper = mapper;
    }

    static PatchBody of(JsonNode body, ObjectMapper mapper) {
        if (body == null || !body.isObject()) {
            throw ValidationException.single("body", "invalid", "Request body must be a JSON object");
        }
        return new PatchBody(body, mapper);
    }

    Patch<String> text(String field) {
        return convert(field, value -> {
            if (!value.isTextual()) {
                throw invalid(field);
            }
            return value.asText();
        });
    }

    Patch<UUID> uuid(String field) {
        return convert(field, value -> read(field, value, UUID.class));
    }

    Patch<Instant> instant(String field) {
        return convert(field, value -> read(field, value, Instant.class));
    }

    Patch<Map<String, Object>> object(String field) {
        return convert(field, value -> {
            if (!value.isObject()) {
                throw invalid(field);
            }
            return mapper.convertValue(value, OBJECT);
        });
    }

    /** Значение как есть (например, BlockDoc — проверяется санитайзером). */
    Patch<Object> raw(String field) {
        return convert(field, value -> mapper.convertValue(value, Object.class));
    }

    <T> Patch<T> value(String field, Class<T> type) {
        return convert(field, value -> read(field, value, type));
    }

    Long version() {
        JsonNode value = node.get(VERSION);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.canConvertToLong()) {
            throw invalid(VERSION);
        }
        return value.asLong();
    }

    private <T> Patch<T> convert(String field, Converter<T> converter) {
        if (!node.has(field)) {
            return Patch.absent();
        }
        JsonNode value = node.get(field);
        if (value.isNull()) {
            return Patch.of(null);
        }
        return Patch.of(converter.convert(value));
    }

    private <T> T read(String field, JsonNode value, Class<T> type) {
        try {
            return mapper.treeToValue(value, type);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw invalid(field);
        }
    }

    private static ValidationException invalid(String field) {
        return ValidationException.single(field, "invalid", "Invalid value");
    }

    @FunctionalInterface
    private interface Converter<T> {
        T convert(JsonNode value);
    }
}
