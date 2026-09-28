package com.tutorcraft.core.files.domain;

import java.util.Arrays;

/** Статус файла: pending — ожидает загрузки в S3, ready — проверен, rejected — не прошёл проверку. */
public enum FileStatus {
    PENDING("pending"), READY("ready"), REJECTED("rejected");

    private final String key;

    FileStatus(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static FileStatus fromKey(String key) {
        return Arrays.stream(values()).filter(status -> status.key.equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown file status"));
    }
}
