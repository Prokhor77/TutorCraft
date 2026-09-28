package com.tutorcraft.core.courses.domain;

import com.tutorcraft.core.shared.domain.Validator;
import java.time.Instant;

/**
 * Самозапись (FR-ENROL-02): вкл/выкл, код доступа, лимит мест, срок записи.
 * Правило гибрида: у платного курса бесплатная самозапись без кода запрещена — код работает как «ваучер».
 */
public record SelfEnrolSettings(boolean enabled, String code, Integer maxStudents, Instant until) {

    public static final SelfEnrolSettings DISABLED = new SelfEnrolSettings(false, null, null, null);
    public static final int MAX_CODE_LENGTH = 64;
    public static final int MAX_STUDENTS_LIMIT = 100_000;

    public SelfEnrolSettings {
        code = code == null || code.isBlank() ? null : code.trim();
    }

    public boolean hasCode() {
        return code != null;
    }

    void validate(Validator validator, boolean paidCourse) {
        validator.maxLength(code, MAX_CODE_LENGTH, "selfEnrol.code")
                .check(maxStudents == null || (maxStudents >= 1 && maxStudents <= MAX_STUDENTS_LIMIT), "selfEnrol.maxStudents",
                        "out_of_range", "maxStudents must be between 1 and " + MAX_STUDENTS_LIMIT)
                .check(!paidCourse || !enabled || hasCode(), "selfEnrol.code", "paid_course_requires_code",
                        "Self-enrolment in a paid course requires an access code");
    }
}
