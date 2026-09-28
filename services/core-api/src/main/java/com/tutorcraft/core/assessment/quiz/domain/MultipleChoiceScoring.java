package com.tutorcraft.core.assessment.quiz.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * Политика частичных баллов для множественного выбора (FR-QBANK-03). Формулы — {@link QuestionGrader}.
 */
public enum MultipleChoiceScoring {
    ALL_OR_NOTHING("all_or_nothing"), PARTIAL("partial"), PARTIAL_WITH_PENALTY("partial_with_penalty");

    private final String key;

    MultipleChoiceScoring(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Optional<MultipleChoiceScoring> find(String key) {
        return Arrays.stream(values()).filter(value -> value.key.equals(key)).findFirst();
    }
}
