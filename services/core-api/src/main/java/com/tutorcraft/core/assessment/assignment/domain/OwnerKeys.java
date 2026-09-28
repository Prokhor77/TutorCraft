package com.tutorcraft.core.assessment.assignment.domain;

import java.util.UUID;

/** Ключ владельца попыток: индивидуальная сдача — студент, групповая — группа. */
public final class OwnerKeys {

    private static final String USER_PREFIX = "u:";
    private static final String GROUP_PREFIX = "g:";

    private OwnerKeys() {
    }

    public static String user(UUID userId) {
        return USER_PREFIX + userId;
    }

    public static String group(UUID groupId) {
        return GROUP_PREFIX + groupId;
    }
}
