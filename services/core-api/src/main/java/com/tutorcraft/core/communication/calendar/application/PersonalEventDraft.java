package com.tutorcraft.core.communication.calendar.application;

import java.time.Instant;

/**
 * Изменение личного события (заметки). Все поля необязательны: null — не менять; clearEnd/clearDescription — очистить.
 * При создании title и startsAt обязательны.
 */
public record PersonalEventDraft(String title, String description, Instant startsAt, Instant endsAt, Boolean allDay,
                                 boolean clearEnd, boolean clearDescription) {

    public static PersonalEventDraft forCreate(String title, String description, Instant startsAt, Instant endsAt,
                                               Boolean allDay) {
        return new PersonalEventDraft(title, description, startsAt, endsAt, allDay, false, false);
    }
}
