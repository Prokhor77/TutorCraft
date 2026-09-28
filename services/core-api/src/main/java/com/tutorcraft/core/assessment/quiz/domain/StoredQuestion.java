package com.tutorcraft.core.assessment.quiz.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Вопрос банка (коллекция {@code questions}) — «голова» с указателем на текущую версию.
 * {@code type} и {@code title} денормализованы из текущей версии для списка и поиска.
 */
public record StoredQuestion(UUID id, UUID tenantId, UUID courseId, UUID categoryId, List<String> tags, QuestionType type,
                             String title, int currentVersion, UUID currentVersionId, Instant createdAt, Instant updatedAt) {
}
