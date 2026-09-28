package com.tutorcraft.core.assessment.quiz.domain;

import java.time.Instant;
import java.util.UUID;

/** Папка банка вопросов курса (FR-QBANK-02). */
public record QuestionCategory(UUID id, UUID tenantId, UUID courseId, UUID parentId, String name, Instant createdAt) {
}
