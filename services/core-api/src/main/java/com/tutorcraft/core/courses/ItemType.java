package com.tutorcraft.core.courses;

import com.tutorcraft.core.shared.domain.ValidationException;
import java.util.Arrays;

/** Типы элементов курса. Материалы — нечего сдавать; активности — есть действие студента (ТЗ 4). */
public enum ItemType {
    PAGE("page", false), FILE("file", false), URL("url", false), FOLDER("folder", false), VIDEO("video", false),
    ASSIGNMENT("assignment", true), QUIZ("quiz", true), FORUM("forum", true);

    private final String key;
    private final boolean activity;

    ItemType(String key, boolean activity) {
        this.key = key;
        this.activity = activity;
    }

    public String key() {
        return key;
    }

    public boolean isActivity() {
        return activity;
    }

    public static ItemType fromKey(String key) {
        return Arrays.stream(values()).filter(type -> type.key.equals(key)).findFirst()
                .orElseThrow(() -> ValidationException.single("type", "invalid_item_type", "Unknown item type"));
    }
}
