package com.tutorcraft.core.courses.application;

import com.tutorcraft.core.courses.domain.CourseModule;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Модули курса в MongoDB (коллекция modules). Все методы, кроме системных, фильтруют по tenantId. */
public interface ModuleRepository {

    void insertAll(Collection<CourseModule> modules);

    /** Не удалённый модуль. */
    Optional<CourseModule> find(UUID tenantId, UUID id);

    Optional<CourseModule> findIncludingDeleted(UUID tenantId, UUID id);

    /** Не удалённые модули курса, по позиции. */
    List<CourseModule> ofCourse(UUID tenantId, UUID courseId);

    List<CourseModule> ofCourses(UUID tenantId, Collection<UUID> courseIds);

    /** Оптимистичная запись редактируемых полей: false — версия не совпала или модуль удалён. */
    boolean update(CourseModule module, long expectedVersion);

    /** Перенос модуля (родитель и позиция), версия увеличивается. */
    void move(UUID tenantId, UUID id, UUID parentId, int position);

    void updatePositions(UUID tenantId, Map<UUID, Integer> positions);

    void softDelete(UUID tenantId, Collection<UUID> ids, Instant at);

    void restore(UUID tenantId, Collection<UUID> ids);

    /** Удалённые модули курса, удалённые после since. */
    List<CourseModule> deletedSince(UUID tenantId, UUID courseId, Instant since);

    /** Системный: физически удаляет модули, удалённые раньше cutoff. */
    long purgeDeletedBefore(Instant cutoff);

    void deleteAllOfCourse(UUID tenantId, UUID courseId);
}
