package com.tutorcraft.core.assessment.assignment.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.files.spi.FileOwnerAccess;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Файлы сдачи читают участники сдачи и пользователи с submission.viewAll в курсе. */
@Component
class SubmissionFileOwnerAccess implements FileOwnerAccess {

    private final SubmissionRepository submissions;
    private final AccessService access;

    SubmissionFileOwnerAccess(SubmissionRepository submissions, AccessService access) {
        this.submissions = submissions;
        this.access = access;
    }

    @Override
    public String ownerType() {
        return SubmissionContent.OWNER_TYPE;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canRead(UUID tenantId, UUID userId, UUID ownerId) {
        return submissions.find(tenantId, ownerId)
                .map(submission -> submissions.isMember(tenantId, submission.id(), userId)
                        || access.permissionsOf(tenantId, userId, AccessContext.course(submission.courseId()))
                                .contains(Permission.SUBMISSION_VIEW_ALL))
                .orElse(false);
    }
}
