package com.tutorcraft.core.assessment.quiz.application;

import com.tutorcraft.core.assessment.quiz.domain.QuizLayout;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Состав тестов (MongoDB: quiz_layouts, документ на элемент курса). */
public interface QuizLayoutRepository {

    Optional<QuizLayout> find(UUID tenantId, UUID itemId);

    void save(UUID tenantId, UUID courseId, UUID itemId, QuizLayout layout);

    /** Число тестов, в которых вопрос стоит фиксированным слотом. */
    Map<UUID, Integer> usage(UUID tenantId, Collection<UUID> questionIds);
}
