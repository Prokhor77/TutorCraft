package com.tutorcraft.core.assessment.quiz.application;

import com.tutorcraft.core.assessment.quiz.domain.QuizOverride;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Исключения теста (PostgreSQL: quiz_overrides). Одно исключение на пользователя/группу в тесте. */
public interface QuizOverrideRepository {

    /** @return сохранённое исключение (id существующего при замене) */
    QuizOverride upsert(UUID tenantId, UUID courseId, UUID itemId, QuizOverride override, UUID actorId);

    List<QuizOverride> listOfItem(UUID tenantId, UUID itemId);

    /** Исключения пользователя и его групп. */
    List<QuizOverride> applicable(UUID tenantId, UUID itemId, UUID userId, Collection<UUID> groupIds);

    boolean delete(UUID tenantId, UUID itemId, UUID overrideId);
}
