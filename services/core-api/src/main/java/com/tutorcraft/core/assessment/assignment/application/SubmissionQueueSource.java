package com.tutorcraft.core.assessment.assignment.application;

import com.tutorcraft.core.assessment.assignment.domain.Submission;
import com.tutorcraft.core.gradebook.spi.GradingQueueSource;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сдачи заданий в единой очереди проверки (FR-GRADE-06). Названия элементов подставляет потребитель
 * (gradebook) — адаптер зависит только от своего репозитория.
 */
@Component
class SubmissionQueueSource implements GradingQueueSource {

    private final SubmissionRepository submissions;

    SubmissionQueueSource(SubmissionRepository submissions) {
        this.submissions = submissions;
    }

    @Override
    @Transactional(readOnly = true)
    public List<QueueEntry> pending(UUID tenantId, Collection<UUID> courseIds) {
        return submissions.awaitingGrading(tenantId, courseIds).stream().map(SubmissionQueueSource::toEntry).toList();
    }

    private static QueueEntry toEntry(Submission submission) {
        return new QueueEntry(QueueEntry.SUBMISSION, submission.id().toString(), submission.courseId(), submission.itemId(),
                null, submission.authorId(), submission.submittedAt(), submission.dueAt(), submission.late());
    }
}
