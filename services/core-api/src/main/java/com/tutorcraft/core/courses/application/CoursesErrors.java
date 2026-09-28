package com.tutorcraft.core.courses.application;

import com.tutorcraft.core.shared.domain.NotFoundException;

/** Коды ошибок модуля courses (тексты — i18n/courses*.properties). */
public final class CoursesErrors {

    public static final String COURSE_NOT_FOUND = "course.not_found";
    public static final String COURSE_HIDDEN = "course.hidden";
    public static final String SHORT_NAME_TAKEN = "course.short_name_taken";
    public static final String SLUG_TAKEN = "course.slug_taken";
    public static final String MODULE_NOT_FOUND = "module.not_found";
    public static final String MODULE_PARENT_DELETED = "module.parent_deleted";
    public static final String ITEM_NOT_FOUND = "item.not_found";
    public static final String ITEM_LOCKED = "item.locked";
    public static final String ITEM_MODULE_DELETED = "item.module_deleted";
    public static final String TRASH_EXPIRED = "trash.expired";
    public static final String COPY_SUFFIX = "courses.copy_suffix";

    private CoursesErrors() {
    }

    public static NotFoundException courseNotFound() {
        return new NotFoundException(COURSE_NOT_FOUND, "Course not found");
    }

    public static NotFoundException moduleNotFound() {
        return new NotFoundException(MODULE_NOT_FOUND, "Module not found");
    }

    public static NotFoundException itemNotFound() {
        return new NotFoundException(ITEM_NOT_FOUND, "Item not found");
    }
}
