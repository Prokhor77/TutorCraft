package com.tutorcraft.core.assessment.assignment.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Попытка сдачи задания. {@code ownerKey} — владелец попыток: студент ('u:&lt;id&gt;') или группа ('g:&lt;id&gt;').
 * {@code dueAt} — действующий срок на момент сдачи; {@code latest} — текущая попытка владельца.
 */
public record Submission(UUID id, UUID tenantId, UUID courseId, UUID itemId, UUID authorId, UUID groupId, String ownerKey,
                         int attemptNo, boolean latest, SubmissionStatus status, Map<String, Object> text,
                         List<UUID> fileIds, Instant submittedAt, Instant dueAt, boolean late, long version,
                         Instant createdAt, Instant updatedAt) {

    public Submission {
        fileIds = fileIds == null ? List.of() : List.copyOf(fileIds);
    }

    public static Submission newDraft(UUID id, UUID tenantId, UUID courseId, UUID itemId, UUID authorId, UUID groupId,
                                      int attemptNo, Instant now) {
        String ownerKey = groupId == null ? OwnerKeys.user(authorId) : OwnerKeys.group(groupId);
        return new Submission(id, tenantId, courseId, itemId, authorId, groupId, ownerKey, attemptNo, true,
                SubmissionStatus.DRAFT, null, List.of(), null, null, false, 0, now, now);
    }

    public boolean hasContent() {
        return !fileIds.isEmpty() || BlockDocContent.hasText(text);
    }

    public Submission withContent(Map<String, Object> newText, List<UUID> newFileIds, Instant now) {
        return new Submission(id, tenantId, courseId, itemId, authorId, groupId, ownerKey, attemptNo, latest, status,
                newText, newFileIds, submittedAt, dueAt, late, version, createdAt, now);
    }

    public Submission submitted(Instant at, Instant effectiveDue, boolean isLate) {
        return new Submission(id, tenantId, courseId, itemId, authorId, groupId, ownerKey, attemptNo, latest,
                SubmissionStatus.submitted(isLate), text, fileIds, at, effectiveDue, isLate, version, createdAt, at);
    }

    public Submission withStatus(SubmissionStatus newStatus, Instant now) {
        return new Submission(id, tenantId, courseId, itemId, authorId, groupId, ownerKey, attemptNo, latest, newStatus,
                text, fileIds, submittedAt, dueAt, late, version, createdAt, now);
    }
}
