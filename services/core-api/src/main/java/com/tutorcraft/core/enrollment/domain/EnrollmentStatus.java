package com.tutorcraft.core.enrollment.domain;

import com.tutorcraft.core.shared.domain.ValidationException;
import java.util.Arrays;

/** Статус записи (FR-ENROL-04): приостановленный теряет доступ, данные сохраняются. */
public enum EnrollmentStatus {
    ACTIVE("active"), SUSPENDED("suspended"), COMPLETED("completed");

    private final String key;

    EnrollmentStatus(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static EnrollmentStatus fromKey(String key) {
        return Arrays.stream(values()).filter(status -> status.key.equals(key)).findFirst()
                .orElseThrow(() -> ValidationException.single("status", "invalid", "Unknown enrollment status"));
    }
}
