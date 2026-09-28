package com.tutorcraft.core.communication.calendar.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.tutorcraft.core.communication.calendar.domain.CalendarEvent.Kind;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** RFC 5545: экранирование TEXT, свёртка строк по 75 октетов, UTC, CRLF (FR-DASH-03). */
class IcsWriterTest {

    private static final Instant STAMP = Instant.parse("2026-09-27T12:00:00Z");

    @Test
    void escapesSpecialCharacters() {
        assertThat(IcsWriter.escape("a\\b;c,d\ne\r\nf")).isEqualTo("a\\\\b\\;c\\,d\\ne\\nf");
        assertThat(IcsWriter.escape(null)).isEmpty();
    }

    @Test
    void foldsLongLinesWithoutSplittingMultibyteCharacters() {
        String line = "SUMMARY:" + "Ж".repeat(60);

        String folded = IcsWriter.fold(line);

        for (String physical : folded.split("\r\n")) {
            assertThat(physical.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75);
        }
        assertThat(folded.replace("\r\n ", "")).isEqualTo(line);
        assertThat(folded.split("\r\n")[1]).startsWith(" ");
    }

    @Test
    void shortLinesAreNotFolded() {
        assertThat(IcsWriter.fold("VERSION:2.0")).isEqualTo("VERSION:2.0");
    }

    @Test
    void writesCalendarWithUtcTimesAndCrlf() {
        UUID id = UUID.fromString("0192f3c1-0000-7000-8000-000000000001");
        CalendarEvent due = new CalendarEvent(id, "Эссе; черновик, v2", Instant.parse("2026-10-01T18:30:00Z"), null,
                UUID.randomUUID(), UUID.randomUUID(), Kind.DUE, "История");
        CalendarEvent personal = new CalendarEvent(UUID.randomUUID(), "Встреча", Instant.parse("2026-10-02T09:00:00Z"),
                Instant.parse("2026-10-02T10:00:00Z"), null, null, Kind.PERSONAL, null);

        String ics = IcsWriter.write("TutorCraft", List.of(due, personal), STAMP, event -> "Срок: " + event.title());

        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n").endsWith("END:VCALENDAR\r\n");
        assertThat(ics).contains("UID:" + id + "@tutorcraft\r\n", "DTSTAMP:20260927T120000Z\r\n",
                "DTSTART:20261001T183000Z\r\n", "SUMMARY:Срок: Эссе\\; черновик\\, v2\r\n", "DESCRIPTION:История\r\n",
                "DTEND:20261002T100000Z\r\n");
        assertThat(ics.split("BEGIN:VEVENT", -1)).hasSize(3);
        assertThat(ics.replace("\r\n", "")).doesNotContain("\n");
    }

    @Test
    void courseEventsAreBuiltInsideRangeWithStableIds() {
        UUID itemId = UUID.randomUUID();
        Instant from = Instant.parse("2026-10-01T00:00:00Z");
        Instant to = Instant.parse("2026-11-01T00:00:00Z");
        CourseCalendar.ItemDates dates = new CourseCalendar.ItemDates(itemId, UUID.randomUUID(), "Эссе", "История",
                Instant.parse("2026-10-10T00:00:00Z"), Instant.parse("2026-09-20T00:00:00Z"), to);

        List<CalendarEvent> events = CourseCalendar.events(List.of(dates), from, to);

        assertThat(events).extracting(CalendarEvent::kind).containsExactly(Kind.DUE);
        assertThat(events.get(0).id()).isEqualTo(CourseCalendar.eventId(itemId, Kind.DUE));
        assertThat(CourseCalendar.eventId(itemId, Kind.DUE)).isNotEqualTo(CourseCalendar.eventId(itemId, Kind.CLOSE));
    }
}
