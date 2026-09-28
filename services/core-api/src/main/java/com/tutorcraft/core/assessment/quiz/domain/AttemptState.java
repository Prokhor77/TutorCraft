package com.tutorcraft.core.assessment.quiz.domain;

import java.util.Arrays;

public enum AttemptState {
    IN_PROGRESS("in_progress"), FINISHED("finished"), ABANDONED("abandoned");

    private final String key;

    AttemptState(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static AttemptState fromKey(String key) {
        return Arrays.stream(values()).filter(state -> state.key.equals(key)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown attempt state " + key));
    }
}
