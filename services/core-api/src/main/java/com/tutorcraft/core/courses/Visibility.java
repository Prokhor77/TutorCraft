package com.tutorcraft.core.courses;

import com.tutorcraft.core.shared.domain.ValidationException;
import java.time.Instant;
import java.util.Arrays;

/** Видимость курса/модуля/элемента (FR-COURSE-04). */
public enum Visibility {
    PUBLISHED("published"), HIDDEN("hidden"), SCHEDULED("scheduled");

    private final String key;

    Visibility(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    /** Видно ли студенту в момент {@code now}. */
    public boolean visibleAt(Instant publishAt, Instant now) {
        return switch (this) {
            case PUBLISHED -> true;
            case HIDDEN -> false;
            case SCHEDULED -> publishAt != null && !now.isBefore(publishAt);
        };
    }

    public static Visibility fromKey(String key) {
        return Arrays.stream(values()).filter(v -> v.key.equals(key)).findFirst()
                .orElseThrow(() -> ValidationException.single("visibility", "invalid", "Unknown visibility"));
    }
}
