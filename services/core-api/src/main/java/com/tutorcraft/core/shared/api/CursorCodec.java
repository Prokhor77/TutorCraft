package com.tutorcraft.core.shared.api;

import com.tutorcraft.core.shared.domain.ValidationException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/** Непрозрачный курсор: base64url("epochMicros|uuid"). */
public final class CursorCodec {

    private static final String SEPARATOR = "|";
    private static final int PARTS = 2;

    private CursorCodec() {
    }

    public record Position(Instant sortKey, UUID id) {
    }

    public static String encode(Position position) {
        String raw = position.sortKey().toString() + SEPARATOR + position.id();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Optional<Position> decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return Optional.empty();
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", PARTS);
            return Optional.of(new Position(Instant.parse(parts[0]), UUID.fromString(parts[1])));
        } catch (IllegalArgumentException | DateTimeParseException | ArrayIndexOutOfBoundsException e) {
            throw ValidationException.single("cursor", "invalid", "Invalid cursor");
        }
    }
}
