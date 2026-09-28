package com.tutorcraft.core.assessment.quiz.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Попытка прохождения теста (таблица {@code quiz_attempts}). */
public record Attempt(UUID id, UUID tenantId, UUID courseId, UUID itemId, UUID userId, int number, AttemptState state,
                      Instant startedAt, Instant timeDue, Instant finishedAt, BigDecimal score, BigDecimal maxScore,
                      boolean needsManualGrading, List<AttemptSlot> layout) {

    public boolean inProgress() {
        return state == AttemptState.IN_PROGRESS;
    }

    public boolean ownedBy(UUID candidate) {
        return userId.equals(candidate);
    }

    public Optional<AttemptSlot> slot(int number) {
        return layout.stream().filter(slot -> slot.slot() == number).findFirst();
    }

    public int totalPages() {
        return layout.stream().mapToInt(AttemptSlot::page).max().orElse(0);
    }
}
