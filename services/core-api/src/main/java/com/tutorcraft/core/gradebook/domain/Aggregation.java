package com.tutorcraft.core.gradebook.domain;

import java.util.Arrays;
import java.util.Optional;

/** Способ агрегирования итоговой оценки курса (FR-GRADE-02). */
public enum Aggregation {
    WEIGHTED_MEAN("weighted_mean"), SUM("sum");

    private final String key;

    Aggregation(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Optional<Aggregation> find(String key) {
        return Arrays.stream(values()).filter(aggregation -> aggregation.key.equals(key)).findFirst();
    }
}
