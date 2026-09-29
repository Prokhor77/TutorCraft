package com.tutorcraft.core.integrations.domain;

import java.util.Arrays;
import java.util.Optional;

/** События исходящих вебхуков (FR-INTEG-02, контракт §14). */
public enum WebhookEvent {
    ENROLLMENT_CREATED("enrollment.created"),
    SUBMISSION_SUBMITTED("submission.submitted"),
    GRADE_PUBLISHED("grade.published"),
    COURSE_COMPLETED("course.completed");

    private final String key;

    WebhookEvent(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Optional<WebhookEvent> find(String key) {
        return Arrays.stream(values()).filter(event -> event.key.equals(key)).findFirst();
    }
}
