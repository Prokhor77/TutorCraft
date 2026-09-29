package com.tutorcraft.core.communication.calendar.domain;

import com.tutorcraft.core.shared.domain.Validator;
import java.time.Instant;

/** Общие правила заметок и занятий: заголовок, текст, порядок времени. */
public final class CalendarRules {

    public static final int MAX_TITLE = 200;
    public static final int MAX_DESCRIPTION = 4000;
    public static final int MAX_ATTENDEES = 500;

    private CalendarRules() {
    }

    public static Validator validate(String title, String description, Instant startsAt, Instant endsAt) {
        return new Validator().notBlank(title, "title").maxLength(title, MAX_TITLE, "title")
                .maxLength(description, MAX_DESCRIPTION, "description")
                .check(startsAt != null, "startsAt", "required", "Start time is required")
                .check(endsAt == null || startsAt == null || !endsAt.isBefore(startsAt), "endsAt", "before_start",
                        "End must not be before start");
    }

    /** Пустой текст хранится как null. */
    public static String normalizeText(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
