package com.tutorcraft.core.courses.domain;

import com.tutorcraft.core.shared.domain.Validator;

/** Ограничения текстовых полей курса, модуля и элемента. */
public final class CourseTexts {

    public static final int MAX_TITLE = 255;
    public static final int MAX_SHORT_NAME = 100;

    private CourseTexts() {
    }

    public static void requireTitle(String title, String field) {
        new Validator().notBlank(title, field).maxLength(title == null ? null : title.trim(), MAX_TITLE, field).throwIfInvalid();
    }

    /** Название копии: исходное + суффикс, обрезанное до допустимой длины. */
    public static String copyTitle(String title, String suffix) {
        String candidate = title + suffix;
        return candidate.length() <= MAX_TITLE ? candidate : title.substring(0, MAX_TITLE - suffix.length()) + suffix;
    }
}
