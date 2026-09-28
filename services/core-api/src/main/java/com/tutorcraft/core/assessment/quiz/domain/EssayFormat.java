package com.tutorcraft.core.assessment.quiz.domain;

import java.util.Arrays;
import java.util.Optional;

public enum EssayFormat {
    TEXT("text"), TEXT_AND_FILES("text_and_files");

    private final String key;

    EssayFormat(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Optional<EssayFormat> find(String key) {
        return Arrays.stream(values()).filter(value -> value.key.equals(key)).findFirst();
    }
}
