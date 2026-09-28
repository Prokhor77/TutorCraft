package com.tutorcraft.core.communication.calendar.domain;

import java.time.Instant;
import java.util.UUID;

/** Событие календаря (контракт CalendarEvent). courseTitle — для описания в iCal. */
public record CalendarEvent(UUID id, String title, Instant startsAt, Instant endsAt, UUID courseId, UUID itemId,
                            Kind kind, String courseTitle) {

    public enum Kind {
        DUE("due"), OPEN("open"), CLOSE("close"), PERSONAL("personal");

        private final String key;

        Kind(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }
}
