package com.tutorcraft.core.shared.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

/** Конвертация Instant ↔ timestamptz. */
public final class Timestamps {

    private Timestamps() {
    }

    public static Timestamp of(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    public static Instant read(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
