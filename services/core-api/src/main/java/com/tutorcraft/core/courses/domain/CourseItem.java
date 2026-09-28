package com.tutorcraft.core.courses.domain;

import com.tutorcraft.core.courses.ItemRef;
import com.tutorcraft.core.courses.ItemType;
import com.tutorcraft.core.courses.Visibility;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Элемент курса — MongoDB, коллекция items. settings — нормализованы ActivityType; content — санитизированный BlockDoc;
 * dueAt/openAt/closeAt вычислены ActivityType при записи.
 */
public record CourseItem(UUID id, UUID tenantId, UUID courseId, UUID moduleId, ItemType type, String title, int position,
                         Visibility visibility, Instant publishAt, Map<String, Object> settings, Map<String, Object> content,
                         Map<String, Object> completionRule, Map<String, Object> conditions, KeyDates dates, long version,
                         Instant deletedAt, Instant createdAt, Instant updatedAt) {

    /** Ключевые даты активности (для «Моих задач», календаря и индекса по сроку). */
    public record KeyDates(Instant dueAt, Instant openAt, Instant closeAt) {

        public static final KeyDates NONE = new KeyDates(null, null, null);
    }

    public CourseItem {
        dates = dates == null ? KeyDates.NONE : dates;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean visibleAt(Instant now) {
        return visibility.visibleAt(publishAt, now);
    }

    public CourseItem withPlacement(UUID newModuleId, int newPosition) {
        return new CourseItem(id, tenantId, courseId, newModuleId, type, title, newPosition, visibility, publishAt, settings,
                content, completionRule, conditions, dates, version, deletedAt, createdAt, updatedAt);
    }

    public CourseItem withTitle(String newTitle) {
        return new CourseItem(id, tenantId, courseId, moduleId, type, newTitle, position, visibility, publishAt, settings,
                content, completionRule, conditions, dates, version, deletedAt, createdAt, updatedAt);
    }

    /** Новое состояние редактируемых полей (inline-правка, UX-04). */
    public CourseItem withContent(ItemContent next) {
        Instant effectivePublishAt = next.visibility() == Visibility.SCHEDULED ? next.publishAt() : null;
        return new CourseItem(id, tenantId, courseId, moduleId, type, next.title(), position, next.visibility(),
                effectivePublishAt, next.settings(), next.content(), next.completionRule(), next.conditions(), next.dates(),
                version, deletedAt, createdAt, updatedAt);
    }

    /**
     * Копия для дублирования (FR-COURSE-06): новый id/курс/модуль, глубокие копии настроек и контента,
     * ссылки на скопированные элементы в условиях и правиле выполнения переназначены по idMapping.
     */
    public CourseItem copy(UUID newId, UUID newCourseId, UUID newModuleId, String newTitle, int newPosition,
                           Map<UUID, UUID> idMapping, Instant now) {
        return new CourseItem(newId, tenantId, newCourseId, newModuleId, type, newTitle, newPosition, visibility, publishAt,
                StructuredValues.copyMap(settings), StructuredValues.copyMap(content),
                StructuredValues.remapMap(completionRule, idMapping), StructuredValues.remapMap(conditions, idMapping),
                dates, 0, null, now, now);
    }

    public ItemContent editableContent() {
        return new ItemContent(title, visibility, publishAt, settings, content, completionRule, conditions, dates);
    }

    public ItemRef toRef() {
        return new ItemRef(id, tenantId, courseId, moduleId, type, title, position, visibility, publishAt, dates.dueAt(),
                dates.openAt(), dates.closeAt(), settings, completionRule, conditions, version);
    }

    /** Редактируемые поля элемента. */
    public record ItemContent(String title, Visibility visibility, Instant publishAt, Map<String, Object> settings,
                              Map<String, Object> content, Map<String, Object> completionRule,
                              Map<String, Object> conditions, KeyDates dates) {
    }
}
