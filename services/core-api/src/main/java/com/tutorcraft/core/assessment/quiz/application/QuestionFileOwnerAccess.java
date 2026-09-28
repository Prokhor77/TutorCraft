package com.tutorcraft.core.assessment.quiz.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.assessment.quiz.domain.StoredQuestion;
import com.tutorcraft.core.files.spi.FileOwnerAccess;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Файлы текста вопроса (владелец 'question'): составители банка и проверяющие курса,
 * а студент — только если вопрос был в его попытке.
 */
@Component
class QuestionFileOwnerAccess implements FileOwnerAccess {

    private final QuestionRepository questions;
    private final AttemptRepository attempts;
    private final AccessService access;

    QuestionFileOwnerAccess(QuestionRepository questions, AttemptRepository attempts, AccessService access) {
        this.questions = questions;
        this.attempts = attempts;
        this.access = access;
    }

    @Override
    public String ownerType() {
        return QuizContent.QUESTION_OWNER;
    }

    @Override
    public boolean canRead(UUID tenantId, UUID userId, UUID ownerId) {
        StoredQuestion question = questions.find(tenantId, ownerId).orElse(null);
        if (question == null) {
            return attempts.userSawQuestion(tenantId, userId, ownerId);
        }
        Set<Permission> permissions = access.permissionsOf(tenantId, userId, AccessContext.course(question.courseId()));
        return permissions.contains(Permission.QBANK_MANAGE) || permissions.contains(Permission.QUIZ_VIEW_REPORTS)
                || attempts.userSawQuestion(tenantId, userId, ownerId);
    }
}
