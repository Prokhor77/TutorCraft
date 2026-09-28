package com.tutorcraft.core.assessment.assignment.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Сдача (контракт §8 Submission). Для студента grade скрыт до публикации (FR-ASSIGN-07). */
public record SubmissionView(UUID id, UUID itemId, UUID userId, String userName, int attemptNo, String status,
                             Map<String, Object> text, List<FileMeta> files, Instant submittedAt, Instant dueAt,
                             boolean late, Grade grade, List<HistoryEntry> history, long version) {

    public record FileMeta(UUID id, String name, long size, String mime, String status, String url) {
    }

    public record Grade(BigDecimal score, BigDecimal maxScore, boolean published, Map<String, Object> feedback,
                        List<FileMeta> feedbackFiles, Instant gradedAt, String graderName) {
    }

    public record HistoryEntry(int attemptNo, String status, Instant submittedAt, BigDecimal score) {
    }

    /** Строка списка сдач для преподавателя (контракт SubmissionSummary). */
    public record Summary(UUID id, UUID userId, String userName, String status, Instant submittedAt, boolean late,
                          BigDecimal score) {
    }
}
