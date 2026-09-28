package com.tutorcraft.core.assessment.quiz.application;

import com.tutorcraft.core.assessment.quiz.domain.QuestionVersion;
import com.tutorcraft.core.assessment.quiz.domain.StoredQuestion;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.api.PageResponse;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Банк вопросов (MongoDB: questions, question_versions). Удалённые вопросы не возвращаются, их версии — да (DATA-02). */
public interface QuestionRepository {

    /** Версия пишется первой, затем голова — повтор после сбоя безопасен (ADR-004). */
    void save(StoredQuestion question, QuestionVersion version);

    Optional<StoredQuestion> find(UUID tenantId, UUID questionId);

    Map<UUID, StoredQuestion> findAll(UUID tenantId, Collection<UUID> questionIds);

    Optional<QuestionVersion> findVersion(UUID tenantId, UUID versionId);

    Map<UUID, QuestionVersion> findVersions(UUID tenantId, Collection<UUID> versionIds);

    List<VersionInfo> versions(UUID tenantId, UUID questionId);

    /** Кандидаты случайного слота: вопросы курса в папке и/или с тегом. */
    List<StoredQuestion> findForRandomSlot(UUID tenantId, UUID courseId, UUID categoryId, String tag);

    PageResponse<StoredQuestion> search(UUID tenantId, UUID courseId, QuestionFilter filter, PageQuery page);

    void markDeleted(UUID tenantId, UUID questionId, Instant at);

    Map<UUID, Long> countByCategory(UUID tenantId, UUID courseId);

    record VersionInfo(UUID id, int version, Instant createdAt) {
    }

    record QuestionFilter(UUID categoryId, String tag, String type, String text) {
    }
}
