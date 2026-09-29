package com.tutorcraft.core.courses.domain;

import com.tutorcraft.core.courses.CourseRef;
import com.tutorcraft.core.courses.Visibility;
import com.tutorcraft.core.shared.domain.Validator;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Курс (PostgreSQL, таблица courses). description — санитизированный BlockDoc. */
public record Course(UUID id, UUID tenantId, UUID categoryId, String title, String shortName, String slug,
                     Map<String, Object> description, UUID coverFileId, Instant startsAt, Instant endsAt,
                     Visibility visibility, Instant publishAt, SelfEnrolSettings selfEnrol,
                     CourseCompletionRule completionRule, GroupMode groupMode, UUID createdBy, long version,
                     Instant createdAt, Instant updatedAt, Instant deletedAt) {

    public Course {
        selfEnrol = selfEnrol == null ? SelfEnrolSettings.DISABLED : selfEnrol;
        completionRule = completionRule == null ? CourseCompletionRule.EMPTY : completionRule;
        groupMode = groupMode == null ? GroupMode.NONE : groupMode;
    }

    /** Новый курс: скрыт до публикации (черновик), без самозаписи. Поля задаются через {@link #toBuilder()}. */
    public static Course blank(UUID id, UUID tenantId, UUID createdBy, Instant now) {
        return new Course(id, tenantId, null, null, null, null, null, null, null, null, Visibility.HIDDEN, null, null,
                null, null, createdBy, 0, now, now, null);
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean visibleToLearnersAt(Instant now) {
        return !isDeleted() && visibility.visibleAt(publishAt, now);
    }

    /** Инварианты курса; бросает ValidationException с путями полей. */
    public void validate() {
        Validator validator = new Validator()
                .notBlank(title, "title").maxLength(title, CourseTexts.MAX_TITLE, "title")
                .maxLength(shortName, CourseTexts.MAX_SHORT_NAME, "shortName")
                .check(startsAt == null || endsAt == null || endsAt.isAfter(startsAt), "endsAt", "before_start",
                        "End date must be after start date")
                .check(visibility != Visibility.SCHEDULED || publishAt != null, "publishAt", "required",
                        "Publish date is required for scheduled visibility");
        selfEnrol.validate(validator);
        completionRule.validate(validator);
        validator.throwIfInvalid();
    }

    public CourseRef toRef() {
        return new CourseRef(id, tenantId, title, shortName, slug, categoryId, visibility, publishAt, startsAt, endsAt,
                completionRule.requiredItemIds(), completionRule.minFinalPercent(), groupMode.key(), createdBy,
                coverFileId);
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    /** Изменение курса без многословных конструкторов (patch, дублирование). */
    public static final class Builder {

        private final Course base;
        private UUID categoryId;
        private String title;
        private String shortName;
        private String slug;
        private Map<String, Object> description;
        private UUID coverFileId;
        private Instant startsAt;
        private Instant endsAt;
        private Visibility visibility;
        private Instant publishAt;
        private SelfEnrolSettings selfEnrol;
        private CourseCompletionRule completionRule;
        private GroupMode groupMode;

        private Builder(Course base) {
            this.base = base;
            this.categoryId = base.categoryId;
            this.title = base.title;
            this.shortName = base.shortName;
            this.slug = base.slug;
            this.description = base.description;
            this.coverFileId = base.coverFileId;
            this.startsAt = base.startsAt;
            this.endsAt = base.endsAt;
            this.visibility = base.visibility;
            this.publishAt = base.publishAt;
            this.selfEnrol = base.selfEnrol;
            this.completionRule = base.completionRule;
            this.groupMode = base.groupMode;
        }

        public Builder categoryId(UUID value) { this.categoryId = value; return this; }
        public Builder title(String value) { this.title = value; return this; }
        public Builder shortName(String value) { this.shortName = value; return this; }
        public Builder slug(String value) { this.slug = value; return this; }
        public Builder description(Map<String, Object> value) { this.description = value; return this; }
        public Builder coverFileId(UUID value) { this.coverFileId = value; return this; }
        public Builder startsAt(Instant value) { this.startsAt = value; return this; }
        public Builder endsAt(Instant value) { this.endsAt = value; return this; }
        public Builder visibility(Visibility value) { this.visibility = value; return this; }
        public Builder publishAt(Instant value) { this.publishAt = value; return this; }
        public Builder selfEnrol(SelfEnrolSettings value) { this.selfEnrol = value; return this; }
        public Builder completionRule(CourseCompletionRule value) { this.completionRule = value; return this; }
        public Builder groupMode(GroupMode value) { this.groupMode = value; return this; }

        public Course build() {
            Instant effectivePublishAt = visibility == Visibility.SCHEDULED ? publishAt : null;
            return new Course(base.id, base.tenantId, categoryId, title == null ? null : title.trim(), normalize(shortName), slug,
                    description, coverFileId, startsAt, endsAt, visibility, effectivePublishAt, selfEnrol,
                    completionRule, groupMode, base.createdBy, base.version, base.createdAt, base.updatedAt, base.deletedAt);
        }

        private static String normalize(String value) {
            return value == null || value.isBlank() ? null : value.trim();
        }
    }
}
