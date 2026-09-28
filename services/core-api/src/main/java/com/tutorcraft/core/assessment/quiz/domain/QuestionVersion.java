package com.tutorcraft.core.assessment.quiz.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Неизменяемая версия вопроса (коллекция {@code question_versions}, FR-QBANK-05, DATA-02). */
public record QuestionVersion(UUID id, UUID tenantId, UUID questionId, int version, QuestionType type, String title,
                              Map<String, Object> body, QuestionData data, BigDecimal defaultScore,
                              Map<String, Object> generalFeedback, Instant createdAt, UUID createdBy) {

    public QuestionCandidate asCandidate() {
        return new QuestionCandidate(questionId, id, defaultScore, data);
    }
}
