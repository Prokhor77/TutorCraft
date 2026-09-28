package com.tutorcraft.core.assessment.assignment.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.assessment.assignment.application.SubmissionRepository.FeedbackOwner;
import com.tutorcraft.core.assessment.assignment.domain.SubmissionStatus;
import com.tutorcraft.core.files.spi.FileOwnerAccess;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Файлы отзыва: проверяющие курса (submission.viewAll) — всегда; участник сдачи — после публикации оценки
 * или возврата на доработку (FR-ASSIGN-07).
 */
@Component
class FeedbackFileOwnerAccess implements FileOwnerAccess {

    private final SubmissionRepository submissions;
    private final AccessService access;

    FeedbackFileOwnerAccess(SubmissionRepository submissions, AccessService access) {
        this.submissions = submissions;
        this.access = access;
    }

    @Override
    public String ownerType() {
        return GradingService.FEEDBACK_OWNER_TYPE;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canRead(UUID tenantId, UUID userId, UUID ownerId) {
        return submissions.feedbackOwner(tenantId, ownerId)
                .map(owner -> visibleToMember(tenantId, userId, owner) || isStaff(tenantId, userId, owner))
                .orElse(false);
    }

    private boolean visibleToMember(UUID tenantId, UUID userId, FeedbackOwner owner) {
        boolean visible = owner.publishedAt() != null || owner.submissionStatus() == SubmissionStatus.RETURNED;
        return visible && submissions.isMember(tenantId, owner.submissionId(), userId);
    }

    private boolean isStaff(UUID tenantId, UUID userId, FeedbackOwner owner) {
        return access.permissionsOf(tenantId, userId, AccessContext.course(owner.courseId()))
                .contains(Permission.SUBMISSION_VIEW_ALL);
    }
}
