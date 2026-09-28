package com.tutorcraft.core.assessment.quiz.domain;

import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;

/** Когда студент видит часть результата (FR-QUIZ-05). */
public enum ReviewTiming {
    IMMEDIATELY("immediately"), AFTER_CLOSE("after_close"), NEVER("never");

    private final String key;

    ReviewTiming(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    /**
     * Показ разрешён для завершённой попытки. «После закрытия» без даты закрытия тест не закрывается — не показываем.
     */
    public boolean allows(Instant closeAt, Instant now) {
        return switch (this) {
            case IMMEDIATELY -> true;
            case AFTER_CLOSE -> closeAt != null && !now.isBefore(closeAt);
            case NEVER -> false;
        };
    }

    public static Optional<ReviewTiming> find(String key) {
        return Arrays.stream(values()).filter(value -> value.key.equals(key)).findFirst();
    }
}
