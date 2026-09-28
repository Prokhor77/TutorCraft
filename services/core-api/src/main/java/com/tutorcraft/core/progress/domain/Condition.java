package com.tutorcraft.core.progress.domain;

import java.time.Instant;
import java.util.UUID;

/** Условие доступа (FR-PROG-02, контракт §5 Condition). */
public sealed interface Condition {

    /** Доступно в [from, until); хотя бы одна граница задана. */
    record DateWindow(Instant from, Instant until) implements Condition {
    }

    /** Элемент выполнен ({@code requireComplete = true}) или не выполнен. */
    record Completion(UUID itemId, boolean requireComplete) implements Condition {
    }

    /** Опубликованная оценка за элемент в процентах: {@code min ≤ p < max}; хотя бы одна граница задана. */
    record Grade(UUID itemId, Double minPercent, Double maxPercent) implements Condition {
    }

    record Group(UUID groupId) implements Condition {
    }
}
