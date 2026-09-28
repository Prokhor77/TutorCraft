package com.tutorcraft.core.communication.calendar.domain;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;

/**
 * Сериализация календаря в iCalendar (RFC 5545): CRLF, экранирование TEXT (\\ ; , и переводов строк),
 * свёртка строк длиннее 75 октетов (UTF-8 без разрыва символов), время — UTC с суффиксом Z.
 */
public final class IcsWriter {

    private static final String CRLF = "\r\n";
    private static final int MAX_LINE_OCTETS = 75;
    private static final String FOLD_PREFIX = " ";
    private static final String PRODID = "-//TutorCraft//Calendar//RU";
    private static final String UID_DOMAIN = "@tutorcraft";
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private IcsWriter() {
    }

    /** @param summaryOf заголовок события (например, «Срок: Эссе») — локализует вызывающий код */
    public static String write(String calendarName, List<CalendarEvent> events, Instant stamp,
                               Function<CalendarEvent, String> summaryOf) {
        StringBuilder out = new StringBuilder();
        line(out, "BEGIN:VCALENDAR");
        line(out, "VERSION:2.0");
        line(out, "PRODID:" + PRODID);
        line(out, "CALSCALE:GREGORIAN");
        line(out, "METHOD:PUBLISH");
        line(out, "X-WR-CALNAME:" + escape(calendarName));
        events.forEach(event -> event(out, event, stamp, summaryOf.apply(event)));
        line(out, "END:VCALENDAR");
        return out.toString();
    }

    private static void event(StringBuilder out, CalendarEvent event, Instant stamp, String summary) {
        line(out, "BEGIN:VEVENT");
        line(out, "UID:" + event.id() + UID_DOMAIN);
        line(out, "DTSTAMP:" + UTC.format(stamp));
        line(out, "DTSTART:" + UTC.format(event.startsAt()));
        if (event.endsAt() != null) {
            line(out, "DTEND:" + UTC.format(event.endsAt()));
        }
        line(out, "SUMMARY:" + escape(summary));
        if (event.courseTitle() != null && !event.courseTitle().isBlank()) {
            line(out, "DESCRIPTION:" + escape(event.courseTitle()));
        }
        line(out, "END:VEVENT");
    }

    /** Экранирование значения типа TEXT (RFC 5545 §3.3.11). */
    public static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n")
                .replace("\r", "\\n");
    }

    /** Свёртка строки (RFC 5545 §3.1): не более 75 октетов, продолжение начинается с пробела. */
    public static String fold(String line) {
        StringBuilder out = new StringBuilder();
        int octets = 0;
        for (int offset = 0; offset < line.length(); ) {
            int codePoint = line.codePointAt(offset);
            String symbol = new String(Character.toChars(codePoint));
            int size = symbol.getBytes(StandardCharsets.UTF_8).length;
            if (octets + size > MAX_LINE_OCTETS) {
                out.append(CRLF).append(FOLD_PREFIX);
                octets = FOLD_PREFIX.length();
            }
            out.append(symbol);
            octets += size;
            offset += Character.charCount(codePoint);
        }
        return out.toString();
    }

    private static void line(StringBuilder out, String content) {
        out.append(fold(content)).append(CRLF);
    }
}
