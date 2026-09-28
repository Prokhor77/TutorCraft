package com.tutorcraft.core.courses.application;

import com.tutorcraft.core.courses.ItemType;
import com.tutorcraft.core.courses.domain.CourseItem;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Элементы курса в MongoDB (коллекция items). Все методы, кроме системных, фильтруют по tenantId. */
public interface ItemRepository {

    void insertAll(Collection<CourseItem> items);

    /** Не удалённый элемент. */
    Optional<CourseItem> find(UUID tenantId, UUID id);

    Optional<CourseItem> findIncludingDeleted(UUID tenantId, UUID id);

    Map<UUID, CourseItem> findAll(UUID tenantId, Collection<UUID> ids);

    /** Не удалённые элементы курса, по позиции. */
    List<CourseItem> ofCourse(UUID tenantId, UUID courseId);

    /** Не удалённые элементы перечисленных модулей, по позиции. */
    List<CourseItem> ofModules(UUID tenantId, Collection<UUID> moduleIds);

    /** Не удалённые элементы курсов; пустой types — все типы. */
    List<CourseItem> ofCourses(UUID tenantId, Collection<UUID> courseIds, Set<ItemType> types);

    /** Оптимистичная запись редактируемых полей: false — версия не совпала или элемент удалён. */
    boolean update(CourseItem item, long expectedVersion, Instant now);

    /** Перенос элемента (модуль и позиция), версия увеличивается. */
    void move(UUID tenantId, UUID id, UUID moduleId, int position, Instant now);

    void updatePositions(UUID tenantId, Map<UUID, Integer> positions);

    void softDelete(UUID tenantId, Collection<UUID> ids, Instant at);

    void restore(UUID tenantId, Collection<UUID> ids, Instant now);

    /** Удалённые элементы курса, удалённые после since. */
    List<CourseItem> deletedSince(UUID tenantId, UUID courseId, Instant since);

    /** Системный: физически удаляет элементы, удалённые раньше cutoff. */
    long purgeDeletedBefore(Instant cutoff);

    void deleteAllOfCourse(UUID tenantId, UUID courseId);
}
