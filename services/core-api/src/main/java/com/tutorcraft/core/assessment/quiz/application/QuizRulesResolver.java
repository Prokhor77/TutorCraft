package com.tutorcraft.core.assessment.quiz.application;

import com.tutorcraft.core.assessment.quiz.application.QuizItems.QuizItem;
import com.tutorcraft.core.assessment.quiz.domain.QuizOverride;
import com.tutorcraft.core.assessment.quiz.domain.QuizRules;
import com.tutorcraft.core.enrollment.EnrollmentApi;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Действующие правила теста для пользователя с учётом его исключений и исключений его групп (FR-QUIZ-06). */
@Component
public class QuizRulesResolver {

    private final QuizOverrideRepository overrides;
    private final EnrollmentApi enrollment;

    public QuizRulesResolver(QuizOverrideRepository overrides, EnrollmentApi enrollment) {
        this.overrides = overrides;
        this.enrollment = enrollment;
    }

    public QuizRules rulesFor(UUID tenantId, QuizItem quiz, UUID userId) {
        Set<UUID> groupIds = enrollment.groupIds(tenantId, quiz.courseId(), userId);
        List<QuizOverride> applicable = overrides.applicable(tenantId, quiz.id(), userId, groupIds);
        return QuizRules.effective(quiz.settings(), applicable);
    }
}
