package com.tutorcraft.core.courses.domain;

import com.tutorcraft.core.shared.domain.Validator;
import java.time.Instant;

/**
 * Самозапись (FR-ENROL-02): вкл/выкл, код доступа, лимит мест, срок записи.
 * Курсы для учеников бесплатны: школа платит только подписку на платформу (ADR-012).
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

    void validate(Validator validator) {
        validator.maxLength(code, MAX_CODE_LENGTH, "selfEnrol.code")
                .check(maxStudents == null || (maxStudents >= 1 && maxStudents <= MAX_STUDENTS_LIMIT), "selfEnrol.maxStudents",
                        "out_of_range", "maxStudents must be between 1 and " + MAX_STUDENTS_LIMIT);
    }
}
