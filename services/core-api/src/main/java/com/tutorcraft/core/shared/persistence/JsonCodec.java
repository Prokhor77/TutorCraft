package com.tutorcraft.core.shared.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.postgresql.util.PGobject;
import org.springframework.stereotype.Component;

/** Сериализация объектов в jsonb и обратно для JDBC-репозиториев. */
@Component
public class JsonCodec {

    private final ObjectMapper objectMapper;

    public JsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public PGobject toJsonb(Object value) {
        if (value == null) {
            return null;
        }
        return Jsonb.of(write(value));
    }

    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON serialization failed for " + value.getClass().getSimpleName(), e);
        }
    }

    public <T> T read(String json, Class<T> type) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON deserialization failed for " + type.getSimpleName(), e);
        }
    }

    public <T> T read(String json, TypeReference<T> type) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON deserialization failed", e);
        }
    }

    public ObjectMapper mapper() {
        return objectMapper;
    }
}
