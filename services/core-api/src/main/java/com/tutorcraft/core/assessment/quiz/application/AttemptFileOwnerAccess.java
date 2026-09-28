package com.tutorcraft.core.assessment.quiz.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.assessment.quiz.domain.Attempt;
import com.tutorcraft.core.files.spi.FileOwnerAccess;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Файлы эссе (владелец 'attempt'): автор попытки и проверяющие курса. */
@Component
class AttemptFileOwnerAccess implements FileOwnerAccess {

    private final AttemptRepository attempts;
    private final AccessService access;

    AttemptFileOwnerAccess(AttemptRepository attempts, AccessService access) {
        this.attempts = attempts;
        this.access = access;
    }

    @Override
    public String ownerType() {
        return QuizContent.ATTEMPT_OWNER;
    }

    @Override
    public boolean canRead(UUID tenantId, UUID userId, UUID ownerId) {
        Attempt attempt = attempts.find(tenantId, ownerId).orElse(null);
        if (attempt == null) {
            return false;
        }
        if (attempt.ownedBy(userId)) {
            return true;
        }
        Set<Permission> permissions = access.permissionsOf(tenantId, userId, AccessContext.course(attempt.courseId()));
        return permissions.contains(Permission.QUIZ_VIEW_REPORTS) || permissions.contains(Permission.SUBMISSION_GRADE);
    }
}
