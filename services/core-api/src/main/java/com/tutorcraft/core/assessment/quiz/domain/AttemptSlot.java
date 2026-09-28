package com.tutorcraft.core.assessment.quiz.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Слот попытки — снимок на момент начала (DATA-02): версия вопроса, страница, баллы и порядок вариантов.
 * Хранится в {@code quiz_attempts.layout}.
 */
public record AttemptSlot(int slot, int page, BigDecimal points, UUID questionId, UUID questionVersionId,
                          List<String> optionOrder) {

    public AttemptSlot {
        optionOrder = optionOrder == null ? List.of() : List.copyOf(optionOrder);
    }
}
