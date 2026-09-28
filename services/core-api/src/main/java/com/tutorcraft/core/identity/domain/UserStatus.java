package com.tutorcraft.core.identity.domain;

import java.util.Arrays;
import java.util.Optional;

/** Статус учётной записи (users.status). */
public enum UserStatus {
    ACTIVE("active"), SUSPENDED("suspended"), INVITED("invited");

    private final String key;

    UserStatus(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Optional<UserStatus> find(String key) {
        return Arrays.stream(values()).filter(status -> status.key.equals(key)).findFirst();
    }

    public static UserStatus fromKey(String key) {
        return find(key).orElseThrow(() -> new IllegalArgumentException("Unknown user status"));
    }
}
