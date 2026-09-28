package com.tutorcraft.core.courses.domain;

import com.tutorcraft.core.shared.domain.ValidationException;
import java.util.Arrays;

/** Групповой режим курса (FR-ENROL-06). */
public enum GroupMode {
    NONE("none"), VISIBLE("visible"), SEPARATE("separate");

    private final String key;

    GroupMode(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static GroupMode fromKey(String key) {
        return Arrays.stream(values()).filter(mode -> mode.key.equals(key)).findFirst()
                .orElseThrow(() -> ValidationException.single("groupMode", "invalid", "Unknown group mode"));
    }
}
