package com.tutorcraft.core.assessment.quiz.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Ответ в слоте попытки (таблица {@code attempt_answers}). Эссе: {@code needsManual = true};
 * ожидает проверки, пока {@code gradedBy == null}.
 */
public record AnswerRecord(UUID attemptId, int slot, UUID questionVersionId, Map<String, Object> response, Double fraction,
                           BigDecimal score, boolean needsManual, UUID gradedBy, String comment, boolean flagged,
                           Instant savedAt) {

    public boolean pendingManualGrading() {
        return needsManual && gradedBy == null;
    }
}
