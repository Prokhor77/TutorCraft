package com.tutorcraft.core.gradebook.application;

import com.tutorcraft.core.gradebook.spi.GradingQueueSource.QueueEntry;
import com.tutorcraft.core.shared.domain.ValidationException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Comparator;
import java.util.Optional;

/**
 * Keyset-курсор очереди проверки. Порядок: срок (без срока — в конце), время сдачи, вид, id.
 * Курсор — base64url("dueAt|submittedAt|kind|id") (ISO-8601 с полной точностью); пустой срок кодируется как «-».
 */
final class QueueCursor {

    static final Comparator<QueueEntry> ORDER = Comparator
            .comparing(QueueEntry::dueAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(QueueEntry::submittedAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(QueueEntry::kind)
            .thenComparing(QueueEntry::id);

    private static final String SEPARATOR = "|";
    private static final String NO_VALUE = "-";
    private static final int PARTS = 4;

    private QueueCursor() {
    }

    static String encode(QueueEntry entry) {
        String raw = String.join(SEPARATOR, iso(entry.dueAt()), iso(entry.submittedAt()), entry.kind(), entry.id());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** Позиция-«ключ» для сравнения: запись с полями последнего элемента предыдущей страницы. */
    static Optional<QueueEntry> decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return Optional.empty();
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", PARTS);
            return Optional.of(new QueueEntry(parts[2], parts[3], null, null, null, null, instant(parts[1]),
                    instant(parts[0]), false));
        } catch (IllegalArgumentException | DateTimeParseException | ArrayIndexOutOfBoundsException e) {
            throw ValidationException.single("cursor", "invalid", "Invalid cursor");
        }
    }

    private static String iso(Instant instant) {
        return instant == null ? NO_VALUE : instant.toString();
    }

    private static Instant instant(String value) {
        return NO_VALUE.equals(value) ? null : Instant.parse(value);
    }
}
