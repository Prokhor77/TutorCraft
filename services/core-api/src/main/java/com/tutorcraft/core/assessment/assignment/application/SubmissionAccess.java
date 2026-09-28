package com.tutorcraft.core.assessment.assignment.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.assessment.assignment.domain.AssignmentErrors;
import com.tutorcraft.core.assessment.assignment.domain.Submission;
import com.tutorcraft.core.shared.domain.NotFoundException;
import com.tutorcraft.core.shared.security.CurrentUser;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Загрузка сдачи и проверка доступа проверяющего (право в курсе + ограничение ассистента группами). */
@Component
class SubmissionAccess {

    private final AccessService access;
    private final SubmissionRepository submissions;
    private final GraderScope scope;

    SubmissionAccess(AccessService access, SubmissionRepository submissions, GraderScope scope) {
        this.access = access;
        this.submissions = submissions;
        this.scope = scope;
    }

    Submission require(UUID tenantId, UUID submissionId) {
        return submissions.find(tenantId, submissionId)
                .orElseThrow(() -> new NotFoundException(AssignmentErrors.SUBMISSION_NOT_FOUND, "Submission not found"));
    }

    void requireStaff(CurrentUser user, AssignmentItem assignment, Submission submission, Permission permission) {
        access.require(permission, AccessContext.course(assignment.courseId()));
        Optional<Set<UUID>> allowed = scope.allowedStudents(user.tenantId(), assignment.courseId(), user.userId());
        scope.requireVisible(allowed, submissions.members(user.tenantId(), submission.id()));
    }
}
