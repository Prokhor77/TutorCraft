package com.tutorcraft.core.shared.domain;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import java.util.UUID;

/** Генерация UUIDv7 (ADR-006). */
public final class Ids {

    private static final TimeBasedEpochGenerator GENERATOR = Generators.timeBasedEpochGenerator();

    private Ids() {
    }

    public static UUID newId() {
        return GENERATOR.generate();
    }

    public static UUID parse(String raw, String field) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw ValidationException.single(field, "invalid_uuid", "Invalid identifier");
        }
    }
}
