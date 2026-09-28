package com.tutorcraft.core.assessment.quiz.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Отложенная публикация оценок теста в момент закрытия (review.whenScore = after_close).
 * PostgreSQL: quiz_grade_releases.
 */
public interface GradeReleaseRepository {

    void schedule(UUID tenantId, UUID courseId, UUID itemId, Instant releaseAt);

    void cancel(UUID tenantId, UUID itemId);

    /** Системная выборка наступивших публикаций всех tenant'ов. */
    List<Release> findDue(Instant now, int limit);

    /** @return false — уже опубликовано другим экземпляром */
    boolean markReleased(UUID tenantId, UUID itemId, Instant at);

    record Release(UUID tenantId, UUID courseId, UUID itemId) {
    }
}
