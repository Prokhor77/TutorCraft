package com.tutorcraft.core.assessment.assignment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Отзыв проверяющего по попытке (FR-ASSIGN-06). Действующий отзыв — последний. */
public record Feedback(UUID id, UUID tenantId, UUID submissionId, UUID graderId, Map<String, Object> text,
                       List<UUID> fileIds, BigDecimal score, boolean returnedForRevision, Instant publishedAt,
                       Instant createdAt) {

    public Feedback {
        fileIds = fileIds == null ? List.of() : List.copyOf(fileIds);
    }

    /**
     * Студент видит отзыв и балл после публикации (FR-ASSIGN-07); возвращённую на доработку работу —
     * сразу, иначе он не узнает, что исправлять.
     */
    public boolean visibleToStudent(SubmissionStatus submissionStatus) {
        return publishedAt != null || submissionStatus == SubmissionStatus.RETURNED;
    }
}
