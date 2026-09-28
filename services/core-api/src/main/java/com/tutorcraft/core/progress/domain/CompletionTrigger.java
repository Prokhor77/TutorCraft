package com.tutorcraft.core.progress.domain;

import java.util.Arrays;
import java.util.Optional;

/** Автоматические условия выполнения элемента (FR-PROG-01, ItemCompletionRule.on). */
public enum CompletionTrigger {
    VIEWED("viewed"), SUBMITTED("submitted"), GRADED("graded"), PASSED("passed"), POSTED("posted");

    private final String key;

    CompletionTrigger(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Optional<CompletionTrigger> find(String key) {
        return Arrays.stream(values()).filter(trigger -> trigger.key.equals(key)).findFirst();
    }
}
