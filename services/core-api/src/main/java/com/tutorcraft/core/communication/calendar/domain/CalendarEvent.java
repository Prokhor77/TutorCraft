package com.tutorcraft.core.communication.calendar.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Событие календаря (контракт CalendarEvent). courseTitle — для описания в iCal; details — текст заметки/занятия,
 * привязка занятия к модулю/элементу и то, может ли текущий пользователь его менять.
 */
public record CalendarEvent(UUID id, String title, Instant startsAt, Instant endsAt, UUID courseId, UUID itemId,
                            Kind kind, String courseTitle, Details details) {

    public CalendarEvent {
        details = details == null ? Details.NONE : details;
    }

    /** Событие без подробностей (ключевые даты элементов курса). */
    public CalendarEvent(UUID id, String title, Instant startsAt, Instant endsAt, UUID courseId, UUID itemId, Kind kind,
                         String courseTitle) {
        this(id, title, startsAt, endsAt, courseId, itemId, kind, courseTitle, Details.NONE);
    }

    public enum Kind {
        DUE("due"), OPEN("open"), CLOSE("close"), PERSONAL("personal"), LESSON("lesson");

        private final String key;

        Kind(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    /**
     * @param attendeeIds ученики занятия (только для тех, кто может его менять; пусто — весь курс)
     * @param version     версия для If-Match (занятия); null — событие не версионируется
     */
    public record Details(String description, boolean allDay, UUID moduleId, String moduleTitle, String itemTitle,
                          LessonAudience audience, List<UUID> attendeeIds, boolean editable, Long version) {

        public static final Details NONE = new Details(null, false, null, null, null, null, List.of(), false, null);

        public Details {
            attendeeIds = attendeeIds == null ? List.of() : List.copyOf(attendeeIds);
        }

        public static Details personal(String description, boolean allDay) {
            return new Details(description, allDay, null, null, null, null, List.of(), true, null);
        }
    }
}
