package com.tutorcraft.core.assessment.assignment.domain;

import java.util.Arrays;

/** Статус попытки сдачи (контракт SubmissionStatus, FR-ASSIGN-04). */
public enum SubmissionStatus {
    DRAFT("draft"), SUBMITTED("submitted"), SUBMITTED_LATE("submitted_late"), GRADED("graded"), RETURNED("returned");

    private final String key;

    SubmissionStatus(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    /** Работа отправлена и ждёт проверки. */
    public boolean awaitsGrading() {
        return this == SUBMITTED || this == SUBMITTED_LATE;
    }

    public static SubmissionStatus fromKey(String key) {
        return Arrays.stream(values()).filter(status -> status.key.equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown submission status"));
    }

    public static SubmissionStatus submitted(boolean late) {
        return late ? SUBMITTED_LATE : SUBMITTED;
    }
}
