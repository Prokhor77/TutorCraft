package com.tutorcraft.core.identity.domain;

import java.util.Arrays;
import java.util.Optional;

/** Как появилась учётная запись (users.created_via). {@code UNKNOWN} — записи, созданные до учёта происхождения. */
public enum AccountOrigin {
    UNKNOWN("unknown"),
    SELF_SIGNUP("self_signup"),
    SCHOOL_OWNER("school_owner"),
    TUTOR_INVITE("tutor_invite"),
    ADMIN("admin"),
    IMPORT("import"),
    SYSTEM("system");

    private final String key;

    AccountOrigin(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Optional<AccountOrigin> find(String key) {
        return Arrays.stream(values()).filter(origin -> origin.key.equals(key)).findFirst();
    }

    public static AccountOrigin fromKey(String key) {
        return find(key).orElse(UNKNOWN);
    }
}
