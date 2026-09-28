package com.tutorcraft.core.communication.forum.domain;

import com.tutorcraft.core.shared.domain.ValidationException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Настройки форума (контракт §11 ForumSettings). Умолчания: обычный форум, правка своего поста 30 минут. */
public record ForumSettings(ForumType forumType, int editWindowMinutes, UUID gradeCategoryId) {

    public static final String KIND = "forum";
    public static final int DEFAULT_EDIT_WINDOW_MINUTES = 30;
    public static final int MAX_EDIT_WINDOW_MINUTES = 7 * 24 * 60;
    private static final String PREFIX = "settings.";

    public static ForumSettings defaults() {
        return new ForumSettings(ForumType.GENERAL, DEFAULT_EDIT_WINDOW_MINUTES, null);
    }

    /** @throws ValidationException поля {@code settings.*} */
    public static ForumSettings parse(Map<String, Object> raw) {
        Map<String, Object> source = raw == null ? Map.of() : raw;
        return new ForumSettings(type(source.get("forumType")), editWindow(source.get("editWindowMinutes")),
                categoryId(source.get("gradeCategoryId")));
    }

    public Duration editWindow() {
        return Duration.ofMinutes(editWindowMinutes);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("kind", KIND);
        map.put("forumType", forumType.key());
        map.put("editWindowMinutes", editWindowMinutes);
        map.put("gradeCategoryId", gradeCategoryId == null ? null : gradeCategoryId.toString());
        return map;
    }

    private static ForumType type(Object raw) {
        if (raw == null) {
            return ForumType.GENERAL;
        }
        return ForumType.find(String.valueOf(raw))
                .orElseThrow(() -> ValidationException.single(PREFIX + "forumType", "invalid", "Unknown forum type"));
    }

    private static int editWindow(Object raw) {
        if (raw == null) {
            return DEFAULT_EDIT_WINDOW_MINUTES;
        }
        if (!(raw instanceof Number number) || number.doubleValue() != Math.rint(number.doubleValue())
                || number.doubleValue() < 0 || number.doubleValue() > MAX_EDIT_WINDOW_MINUTES) {
            throw ValidationException.single(PREFIX + "editWindowMinutes", "out_of_range",
                    "Edit window must be an integer between 0 and " + MAX_EDIT_WINDOW_MINUTES);
        }
        return number.intValue();
    }

    private static UUID categoryId(Object raw) {
        if (raw == null || raw instanceof UUID) {
            return (UUID) raw;
        }
        try {
            return UUID.fromString(String.valueOf(raw));
        } catch (IllegalArgumentException e) {
            throw ValidationException.single(PREFIX + "gradeCategoryId", "invalid_uuid", "Invalid identifier");
        }
    }
}
