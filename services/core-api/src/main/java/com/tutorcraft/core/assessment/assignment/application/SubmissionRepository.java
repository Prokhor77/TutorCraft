package com.tutorcraft.core.assessment.assignment.application;

import com.tutorcraft.core.assessment.assignment.domain.Feedback;
import com.tutorcraft.core.assessment.assignment.domain.Submission;
import com.tutorcraft.core.assessment.assignment.domain.SubmissionStatus;
import com.tutorcraft.core.shared.api.PageQuery;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Хранилище сдач, участников, файлов и отзывов. Все методы фильтруют по tenant (DATA-01). */
public interface SubmissionRepository {

    Optional<Submission> find(UUID tenantId, UUID submissionId);

    /** Текущая попытка владельца с блокировкой строки (сериализация сдачи, AC-3). */
    Optional<Submission> lockLatest(UUID tenantId, UUID itemId, String ownerKey);

    Optional<Submission> findLatest(UUID tenantId, UUID itemId, String ownerKey);

    /** Текущая попытка, в которой пользователь участник (индивидуальная или групповая). */
    Optional<Submission> findLatestForMember(UUID tenantId, UUID itemId, UUID userId);

    /** @return false — попытка с таким номером уже создана параллельным запросом */
    boolean insert(Submission submission);

    void addMembers(UUID tenantId, UUID submissionId, Collection<UUID> userIds);

    List<UUID> members(UUID tenantId, UUID submissionId);

    boolean isMember(UUID tenantId, UUID submissionId, UUID userId);

    void markSuperseded(UUID tenantId, UUID submissionId);

    /** Оптимистичное обновление содержимого/статуса. @return false — версия изменилась */
    boolean update(Submission submission, long expectedVersion);

    void replaceFiles(UUID tenantId, UUID submissionId, List<UUID> fileIds);

    List<Submission> attempts(UUID tenantId, UUID itemId, String ownerKey);

    List<Submission> listLatest(UUID tenantId, UUID itemId, SubmissionFilter filter, PageQuery page);

    /** Статусы текущих попыток пользователя по элементам. */
    Map<UUID, SubmissionStatus> latestStatusesForMember(UUID tenantId, UUID userId, Collection<UUID> itemIds);

    /** Отправленные и не проверенные текущие попытки в курсах (очередь проверки). */
    List<Submission> awaitingGrading(UUID tenantId, Collection<UUID> courseIds);

    void insertFeedback(Feedback feedback);

    Optional<Feedback> latestFeedback(UUID tenantId, UUID submissionId);

    Map<UUID, Feedback> latestFeedbacks(UUID tenantId, Collection<UUID> submissionIds);

    Optional<FeedbackOwner> feedbackOwner(UUID tenantId, UUID feedbackId);

    int publishFeedback(UUID tenantId, UUID itemId, Instant at);

    /** Опубликованные отзывы текущих попыток пользователя по элементам (для «Моих оценок»). */
    Map<UUID, Map<String, Object>> publishedFeedbackTexts(UUID tenantId, UUID userId, Collection<UUID> itemIds);

    /**
     * Фильтр списка сдач (FR-ASSIGN-05). statuses пусто — все; notGraded — ждут проверки; lateOnly — только опоздавшие;
     * memberIds null — без ограничения по участникам.
     */
    record SubmissionFilter(Set<SubmissionStatus> statuses, boolean lateOnly, Set<UUID> memberIds) {
    }

    record FeedbackOwner(UUID submissionId, UUID courseId, SubmissionStatus submissionStatus, Instant publishedAt) {
    }
}
