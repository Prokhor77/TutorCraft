package com.tutorcraft.core.courses.application;

import com.tutorcraft.core.courses.domain.Course;
import com.tutorcraft.core.shared.api.PageQuery;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Курсы в PostgreSQL. Все методы, кроме помеченных «системный», фильтруют по tenant (DATA-01). */
public interface CourseRepository {

    /** @throws com.tutorcraft.core.shared.domain.ConflictException slug или краткое имя заняты */
    void insert(Course course);

    /** Оптимистичная запись: false — версия не совпала или курс удалён. Версия увеличивается на 1. */
    boolean update(Course course, long expectedVersion, Instant now);

    /** Не удалённый курс. */
    Optional<Course> find(UUID tenantId, UUID id);

    Optional<Course> findIncludingDeleted(UUID tenantId, UUID id);

    Map<UUID, Course> findAll(UUID tenantId, Collection<UUID> ids);

    Optional<Course> findByShortName(UUID tenantId, String shortName);

    Optional<Course> findBySlug(UUID tenantId, String slug);

    /** Занятые slug, начинающиеся с base (включая удалённые курсы). */
    Set<String> slugsStartingWith(UUID tenantId, String base);

    boolean shortNameTaken(UUID tenantId, String shortName, UUID exceptCourseId);

    Map<UUID, Integer> countByCategory(UUID tenantId);

    /** Категории, в которых есть курсы tenant (для вычисления области видимости менеджера категории). */
    List<UUID> usedCategoryIds(UUID tenantId);

    /** Страница курсов в области видимости, keyset (created_at DESC, id DESC). */
    List<Course> list(UUID tenantId, CourseFilter filter, PageQuery page, Instant now);

    /** Опубликованные (видимые студентам в now) курсы tenant для публичной витрины. */
    List<Course> published(UUID tenantId, Instant now);

    void softDelete(UUID tenantId, UUID id, Instant now);

    void restore(UUID tenantId, UUID id, Instant now);

    /** Удалённые после since курсы в области видимости. */
    List<Course> deletedSince(UUID tenantId, CourseScope scope, Instant since);

    /** Пусто — курса нет (или удалён) в tenant; внутреннее значение — категория. */
    Optional<Optional<UUID>> categoryOf(UUID tenantId, UUID id);

    boolean existsInOtherTenant(UUID tenantId, UUID id);

    /**
     * Системный (очистка корзины, все tenant): страница курсов, удалённых раньше cutoff, по (deleted_at, id) после
     * {@code after} (null — с начала). Удерживаемые курсы остаются в корзине, поэтому обход идёт по курсору.
     */
    List<TenantCourseId> deletedBefore(Instant cutoff, TenantCourseId after, int limit);

    /** Физическое удаление курса из корзины (в транзакции вызывающего кода). @return false — курса уже нет. */
    boolean hardDelete(UUID tenantId, UUID id);

    /** Фильтр списка курсов: q — подстрока названия/краткого имени. */
    record CourseFilter(String q, UUID categoryId, CourseScope scope) {
    }

    /**
     * Область видимости: all — все курсы tenant; иначе курсы, где пользователь — персонал (staff),
     * курсы категорий под его управлением, и курсы, где он учащийся, — только если они видимы студентам.
     */
    record CourseScope(boolean all, Collection<UUID> staffCourseIds, Collection<UUID> learnerCourseIds,
                       Collection<UUID> categoryIds) {

        public static CourseScope everything() {
            return new CourseScope(true, List.of(), List.of(), List.of());
        }
    }

    record TenantCourseId(UUID tenantId, UUID courseId, Instant deletedAt) {
    }
}
