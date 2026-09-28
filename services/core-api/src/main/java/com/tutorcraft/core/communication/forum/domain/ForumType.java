package com.tutorcraft.core.communication.forum.domain;

import java.util.Arrays;
import java.util.Optional;

/** Тип форума (FR-FORUM-02). */
public enum ForumType {
    GENERAL("general"), QA("qa"), ANNOUNCEMENTS("announcements");

    private final String key;

    ForumType(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Optional<ForumType> find(String key) {
        return Arrays.stream(values()).filter(type -> type.key.equals(key)).findFirst();
    }
}
