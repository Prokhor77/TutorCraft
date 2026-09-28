package com.tutorcraft.core.activity.domain;

import java.util.Arrays;
import java.util.Optional;

/** Фильтр по результату действия. */
public enum ActivityOutcome {
    ALL("all"),
    /** Отклонённые запросы (4xx) и все ошибки. */
    FAILED("failed"),
    /** Только ошибки системы: ответы 5xx и ошибки в браузере. */
    ERRORS("errors");

    private final String key;

    ActivityOutcome(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Optional<ActivityOutcome> fromKey(String key) {
        return Arrays.stream(values()).filter(outcome -> outcome.key.equals(key)).findFirst();
    }
}
