package com.tutorcraft.core.assessment.assignment.application;

import com.tutorcraft.core.gradebook.spi.GradeFeedbackSource;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Опубликованные отзывы по заданиям для «Моих оценок» (FR-GRADE-08). */
@Component
class AssignmentFeedbackSource implements GradeFeedbackSource {

    private final SubmissionRepository submissions;

    AssignmentFeedbackSource(SubmissionRepository submissions) {
        this.submissions = submissions;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Map<String, Object>> publishedFeedback(UUID tenantId, UUID userId, Collection<UUID> sourceItemIds) {
        return submissions.publishedFeedbackTexts(tenantId, userId, sourceItemIds);
    }
}
