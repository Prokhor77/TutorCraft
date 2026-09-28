package com.tutorcraft.core.assessment.quiz.application;

import com.tutorcraft.core.assessment.quiz.domain.AnswerRecord;
import com.tutorcraft.core.assessment.quiz.domain.Attempt;
import com.tutorcraft.core.assessment.quiz.domain.GradingMethod.ScoredAttempt;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.api.PageResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Попытки и ответы (PostgreSQL: quiz_attempts, attempt_answers). */
public interface AttemptRepository {

    /**
     * Вставляет попытку и пустые строки ответов для всех слотов.
     * @return false — у пользователя уже есть незавершённая попытка (гонка параллельного старта)
     */
    boolean insert(Attempt attempt);

    Optional<Attempt> find(UUID tenantId, UUID attemptId);

    /** Блокирует строку попытки до конца транзакции (завершение, проверка эссе). */
    Optional<Attempt> lock(UUID tenantId, UUID attemptId);

    Optional<Attempt> findInProgress(UUID tenantId, UUID itemId, UUID userId);

    /** Сколько попыток пользователь уже начинал (все состояния). */
    int countAttempts(UUID tenantId, UUID itemId, UUID userId);

    /**
     * Автосохранение одним UPDATE (NFR-PERF-02): только владелец, только незавершённая попытка, не позже {@code cutoff}.
     * @return false — условие не выполнено (причину выясняет вызывающий)
     */
    boolean saveAnswer(AnswerSave save);

    List<AnswerRecord> answers(UUID tenantId, UUID attemptId);

    Optional<AnswerRecord> answer(UUID tenantId, UUID attemptId, int slot);

    void updateAnswerGrade(UUID tenantId, AnswerGrade grade);

    /** @return false — попытка уже не в процессе */
    boolean markFinished(UUID tenantId, UUID attemptId, Instant finishedAt, BigDecimal score, boolean needsManual);

    void updateScore(UUID tenantId, UUID attemptId, BigDecimal score, boolean needsManual);

    List<ScoredAttempt> finishedScores(UUID tenantId, UUID itemId, UUID userId);

    boolean hasPendingManual(UUID tenantId, UUID itemId, UUID userId);

    List<Attempt> finishedOfItem(UUID tenantId, UUID itemId);

    PageResponse<Attempt> pageOfItem(UUID tenantId, UUID itemId, PageQuery page);

    /**
     * Системная выборка просроченных незавершённых попыток всех tenant'ов (AC-4). Конкурентное завершение безопасно:
     * {@link #lock} + проверка состояния в {@code AttemptFinisher}.
     */
    List<AttemptKey> findExpired(Instant cutoff, int limit);

    Map<UUID, ItemAttemptState> itemStates(UUID tenantId, UUID userId, Collection<UUID> itemIds);

    List<PendingEssay> pendingEssays(UUID tenantId, Collection<UUID> courseIds);

    /** Вопрос встречался пользователю в какой-либо его попытке (доступ к файлам текста вопроса). */
    boolean userSawQuestion(UUID tenantId, UUID userId, UUID questionId);

    record AnswerSave(UUID tenantId, UUID userId, UUID attemptId, int slot, Map<String, Object> response, boolean flagged,
                      Instant savedAt, Instant cutoff) {
    }

    record AnswerGrade(UUID attemptId, int slot, UUID questionVersionId, Double fraction, BigDecimal score, boolean needsManual,
                       UUID gradedBy, String comment, Instant gradedAt) {
    }

    record AttemptKey(UUID tenantId, UUID attemptId) {
    }

    record ItemAttemptState(boolean inProgress, int finished, boolean pendingManual) {
    }

    record PendingEssay(UUID attemptId, int slot, UUID courseId, UUID itemId, UUID userId, Instant finishedAt) {
    }
}
