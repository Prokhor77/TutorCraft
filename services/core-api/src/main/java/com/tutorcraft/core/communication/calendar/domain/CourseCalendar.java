package com.tutorcraft.core.communication.calendar.domain;

import com.tutorcraft.core.communication.calendar.domain.CalendarEvent.Kind;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** События курсов из ключевых дат элементов (FR-DASH-03): срок, открытие, закрытие — в пределах [from, to). */
public final class CourseCalendar {

    private static final String ID_SEPARATOR = ":";

    private CourseCalendar() {
    }

    public record ItemDates(UUID itemId, UUID courseId, String title, String courseTitle, Instant dueAt, Instant openAt,
                            Instant closeAt) {
    }

    public static List<CalendarEvent> events(List<ItemDates> items, Instant from, Instant to) {
        List<CalendarEvent> events = new ArrayList<>();
        for (ItemDates item : items) {
            add(events, item, Kind.OPEN, item.openAt(), from, to);
            add(events, item, Kind.DUE, item.dueAt(), from, to);
            add(events, item, Kind.CLOSE, item.closeAt(), from, to);
        }
        events.sort(Comparator.comparing(CalendarEvent::startsAt).thenComparing(CalendarEvent::id));
        return events;
    }

    /** Стабильный id события элемента: одинаковый при каждом запросе (UID в iCal). */
    public static UUID eventId(UUID itemId, Kind kind) {
        return UUID.nameUUIDFromBytes((itemId + ID_SEPARATOR + kind.key()).getBytes(StandardCharsets.UTF_8));
    }

    private static void add(List<CalendarEvent> events, ItemDates item, Kind kind, Instant at, Instant from, Instant to) {
        if (at == null || at.isBefore(from) || !at.isBefore(to)) {
            return;
        }
        events.add(new CalendarEvent(eventId(item.itemId(), kind), item.title(), at, null, item.courseId(), item.itemId(), kind,
                item.courseTitle()));
    }
}
