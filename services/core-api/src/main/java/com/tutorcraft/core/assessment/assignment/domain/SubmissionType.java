package com.tutorcraft.core.assessment.assignment.domain;

import java.util.Arrays;
import java.util.Optional;

/** Способ сдачи задания (FR-ASSIGN-01): файлы, текст онлайн, оба, без сдачи (офлайн-оценка). */
public enum SubmissionType {
    FILE("file", true, false), TEXT("text", false, true), BOTH("both", true, true), NONE("none", false, false);

    private final String key;
    private final boolean filesAllowed;
    private final boolean textAllowed;

    SubmissionType(String key, boolean filesAllowed, boolean textAllowed) {
        this.key = key;
        this.filesAllowed = filesAllowed;
        this.textAllowed = textAllowed;
    }

    public String key() {
        return key;
    }

    public boolean filesAllowed() {
        return filesAllowed;
    }

    public boolean textAllowed() {
        return textAllowed;
    }

    public boolean acceptsOnlineSubmission() {
        return filesAllowed || textAllowed;
    }

    public static Optional<SubmissionType> find(String key) {
        return Arrays.stream(values()).filter(type -> type.key.equals(key)).findFirst();
    }
}
