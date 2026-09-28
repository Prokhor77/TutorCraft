package com.tutorcraft.core.assessment.quiz.domain;

import java.time.Instant;
import java.util.UUID;

/** Индивидуальное исключение для студента или группы (FR-QUIZ-06). Поле null — «как в настройках теста». */
public record QuizOverride(UUID id, UUID userId, UUID groupId, Instant openAt, Instant closeAt, Integer timeLimitSec,
                           Integer maxAttempts) {

    public boolean forUser() {
        return userId != null;
    }
}
