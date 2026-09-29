package com.tutorcraft.core.enrollment.application;

import com.tutorcraft.core.access.domain.CourseRole;
import com.tutorcraft.core.enrollment.domain.Enrollment;
import com.tutorcraft.core.shared.api.PageQuery;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Записи на курс (PostgreSQL). Все методы фильтруют по tenant (DATA-01). */
public interface EnrollmentRepository {

    /** Создание или обновление записи по (course, user); поведение при существующей записи — {@link UpsertMode}. */
    UpsertResult upsert(Enrollment enrollment, UpsertMode mode);

    Optional<Enrollment> find(UUID tenantId, UUID id);

    Optional<Enrollment> findByCourseAndUser(UUID tenantId, UUID courseId, UUID userId);

    List<Enrollment> list(UUID tenantId, UUID courseId, EnrollmentFilter filter, PageQuery page);

    void update(Enrollment enrollment, Instant now);

    void delete(UUID tenantId, UUID id);

    /** Записи, дающие доступ в now (status=active и окно дат). Пустой roles — любые роли. */
    List<Enrollment> activeInCourse(UUID tenantId, UUID courseId, Set<CourseRole> roles, Instant now);

    List<UUID> activeCourseIds(UUID tenantId, UUID userId, Set<CourseRole> roles, Instant now);

    Optional<String> activeRoleKey(UUID tenantId, UUID userId, UUID courseId, Instant now);

    /** Пользователи, у которых есть запись на курс (любой статус). */
    Set<UUID> enrolledUserIds(UUID tenantId, UUID courseId, Collection<UUID> userIds);

    int countActiveByRole(UUID tenantId, UUID courseId, CourseRole role);

    void touchLastAccess(UUID tenantId, UUID courseId, UUID userId, Instant now);

    /** Сериализует конкурентную запись на курс (лимит мест) до конца транзакции. */
    void lockCourse(UUID courseId);

    enum UpsertMode {
        /** Ручная запись: роль, даты и статус active перезаписываются. */
        OVERRIDE,
        /** Запись по API (импорт, приглашение): активная запись не меняется, неактивная реактивируется. */
        REACTIVATE
    }

    record UpsertResult(UUID id, boolean created, String roleKey) {
    }

    record EnrollmentFilter(String q, String roleKey, UUID groupId) {
    }
}
