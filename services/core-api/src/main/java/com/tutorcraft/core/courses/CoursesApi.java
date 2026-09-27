package com.tutorcraft.core.courses;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Публичный API модуля courses. Все методы фильтруют по tenant (DATA-01); удалённое в корзину не возвращается. */
public interface CoursesApi {

    /** @throws com.tutorcraft.core.shared.domain.NotFoundException code {@code course.not_found} */
    CourseRef requireCourse(UUID tenantId, UUID courseId);

    Optional<CourseRef> findCourse(UUID tenantId, UUID courseId);

    Optional<CourseRef> findByShortName(UUID tenantId, String shortName);

    Map<UUID, CourseRef> findCourses(UUID tenantId, Collection<UUID> courseIds);

    Map<UUID, Integer> countByCategory(UUID tenantId);

    /** @throws com.tutorcraft.core.shared.domain.NotFoundException code {@code item.not_found} */
    ItemRef requireItem(UUID tenantId, UUID itemId);

    Map<UUID, ItemRef> findItems(UUID tenantId, Collection<UUID> itemIds);

    List<ItemRef> itemsOfCourse(UUID tenantId, UUID courseId);

    List<ModuleRef> modulesOfCourse(UUID tenantId, UUID courseId);

    /** Элементы указанных типов во всех перечисленных курсах. */
    List<ItemRef> itemsOfCourses(UUID tenantId, Collection<UUID> courseIds, Set<ItemType> types);

    /** Элемент виден студенту: курс, модуль (и родитель) и элемент опубликованы (без условий доступа). */
    boolean isVisibleToLearners(UUID tenantId, ItemRef item);
}
