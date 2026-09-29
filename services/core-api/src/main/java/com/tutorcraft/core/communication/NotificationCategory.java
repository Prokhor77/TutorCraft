package com.tutorcraft.core.communication;

import java.util.Arrays;
import java.util.Optional;

/** Категории уведомлений (FR-NOTIF-01/02 + гибрид). */
public enum NotificationCategory {
    NEW_ITEM("new_item"), DEADLINE("deadline"), GRADE_PUBLISHED("grade_published"), FORUM_REPLY("forum_reply"),
    ANNOUNCEMENT("announcement"), SUBMISSION_RECEIVED("submission_received"), VIDEO_READY("video_ready"),
    ACCOUNT("account");

    private final String key;

    NotificationCategory(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Optional<NotificationCategory> fromKey(String key) {
        return Arrays.stream(values()).filter(c -> c.key.equals(key)).findFirst();
    }
}
