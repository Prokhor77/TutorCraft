package com.tutorcraft.core.assessment.quiz.domain;

import com.tutorcraft.core.shared.domain.ValidationException;
import java.util.Arrays;
import java.util.Optional;

/** Типы вопросов MVP (FR-QBANK-03). */
public enum QuestionType {
    SINGLE_CHOICE("single_choice"), MULTIPLE_CHOICE("multiple_choice"), TRUE_FALSE("true_false"),
    SHORT_ANSWER("short_answer"), NUMERICAL("numerical"), ESSAY("essay"), MATCHING("matching"), ORDERING("ordering");

    private final String key;

    QuestionType(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Optional<QuestionType> find(String key) {
        return Arrays.stream(values()).filter(type -> type.key.equals(key)).findFirst();
    }

    public static QuestionType fromKey(String key, String field) {
        return find(key).orElseThrow(() -> ValidationException.single(field, "invalid_question_type", "Unknown question type"));
    }
}
