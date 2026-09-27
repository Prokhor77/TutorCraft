package com.tutorcraft.core.shared.persistence;

import java.sql.SQLException;
import org.postgresql.util.PGobject;

/** Параметр jsonb для JDBC. */
public final class Jsonb {

    private static final String JSONB = "jsonb";

    private Jsonb() {
    }

    public static PGobject of(String json) {
        if (json == null) {
            return null;
        }
        try {
            PGobject object = new PGobject();
            object.setType(JSONB);
            object.setValue(json);
            return object;
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot build jsonb parameter", e);
        }
    }
}
