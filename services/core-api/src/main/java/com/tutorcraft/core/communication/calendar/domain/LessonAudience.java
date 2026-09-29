package com.tutorcraft.core.communication.calendar.domain;

import java.util.Arrays;
import java.util.Optional;

/** Кому назначено занятие: всем ученикам курса или выбранным. */
public enum LessonAudience {
    COURSE("course"), STUDENTS("students");

    private final String key;

    LessonAudience(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Optional<LessonAudience> fromKey(String key) {
        return Arrays.stream(values()).filter(audience -> audience.key.equals(key)).findFirst();
    }
}
