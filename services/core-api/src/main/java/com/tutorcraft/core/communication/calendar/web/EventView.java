package com.tutorcraft.core.communication.calendar.web;

import com.tutorcraft.core.communication.calendar.domain.CalendarEvent;
import com.tutorcraft.core.communication.calendar.domain.CalendarEvent.Details;
import com.tutorcraft.core.communication.calendar.domain.LessonAudience;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Контракт CalendarEvent. Для занятий: courseTitle/moduleTitle/itemTitle, audience; attendeeIds — только тем, кто может
 * менять занятие; version — для If-Match. canEdit — событие можно изменить или удалить из календаря.
 */
record EventView(UUID id, String title, Instant startsAt, Instant endsAt, UUID courseId, UUID itemId, String kind,
                 String description, boolean allDay, String courseTitle, UUID moduleId, String moduleTitle,
                 String itemTitle, String audience, List<UUID> attendeeIds, boolean canEdit, Long version) {

    static EventView of(CalendarEvent event) {
        Details details = event.details();
        LessonAudience audience = details.audience();
        return new EventView(event.id(), event.title(), event.startsAt(), event.endsAt(), event.courseId(), event.itemId(),
                event.kind().key(), details.description(), details.allDay(), event.courseTitle(), details.moduleId(),
                details.moduleTitle(), details.itemTitle(), audience == null ? null : audience.key(),
                details.attendeeIds(), details.editable(), details.version());
    }
}
