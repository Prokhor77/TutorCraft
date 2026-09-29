package com.tutorcraft.core.courses;

import java.time.Instant;
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

    /**
     * Системный запрос (все tenant, без текущего пользователя): элементы со сроком сдачи в интервале
     * (fromExclusive, toInclusive] — для напоминаний о дедлайнах (FR-NOTIF-01). Элементы и модули в корзине и элементы
     * курсов в корзине не возвращаются. Видимость студентам (публикация курса/модуля/элемента) НЕ проверяется —
     * вызывающий код проверяет её через {@link #isVisibleToLearners(UUID, ItemRef)} в момент отправки.
     */
    List<ItemRef> itemsDueBetween(Instant fromExclusive, Instant toInclusive);

    /** Элемент виден студенту: курс, модуль (и родитель) и элемент опубликованы (без условий доступа). */
    boolean isVisibleToLearners(UUID tenantId, ItemRef item);

    /** Настройки самозаписи курса (FR-ENROL-02); пусто — курса нет. Код доступа — для проверки модулем enrollment. */
    Optional<SelfEnrolment> selfEnrolment(UUID tenantId, UUID courseId);

    /**
     * Самозапись: {@code code != null} — требуется код; {@code maxStudents}/{@code until} — лимит мест и срок записи.
     */
    record SelfEnrolment(boolean enabled, String code, Integer maxStudents, Instant until) {
    }
}
