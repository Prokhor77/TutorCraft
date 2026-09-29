package com.tutorcraft.core.communication.calendar.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Адресация и перенос занятий. */
class LessonTest {

    private static final Instant START = Instant.parse("2026-10-05T15:00:00Z");
    private static final Instant END = Instant.parse("2026-10-05T16:00:00Z");
    private static final UUID ANNA = UUID.randomUUID();
    private static final UUID BORIS = UUID.randomUUID();
    private static final UUID GONE = UUID.randomUUID();

    @Test
    void courseLessonIsAddressedToEveryoneAndIgnoresAttendees() {
        Lesson lesson = lesson(LessonAudience.COURSE, List.of(ANNA), START, END);

        assertThat(lesson.attendeeIds()).isEmpty();
        assertThat(lesson.addressedTo(BORIS)).isTrue();
        assertThat(lesson.recipients(Set.of(ANNA, BORIS))).containsExactlyInAnyOrder(ANNA, BORIS);
    }

    @Test
    void studentsLessonIsAddressedOnlyToActiveAttendees() {
        Lesson lesson = lesson(LessonAudience.STUDENTS, List.of(ANNA, GONE), START, END);

        assertThat(lesson.addressedTo(ANNA)).isTrue();
        assertThat(lesson.addressedTo(BORIS)).isFalse();
        assertThat(lesson.recipients(Set.of(ANNA, BORIS))).containsExactly(ANNA);
    }

    @Test
    void reschedulingIsDetectedByStartOrEnd() {
        Lesson original = lesson(LessonAudience.COURSE, List.of(), START, END);

        assertThat(lesson(LessonAudience.COURSE, List.of(), START, END).rescheduledFrom(original)).isFalse();
        assertThat(lesson(LessonAudience.COURSE, List.of(), START.plusSeconds(60), END).rescheduledFrom(original)).isTrue();
        assertThat(lesson(LessonAudience.COURSE, List.of(), START, null).rescheduledFrom(original)).isTrue();
    }

    @Test
    void rulesRejectEndBeforeStartAndTooLongText() {
        assertThat(CalendarRules.validate("Урок", null, START, END).hasErrors()).isFalse();
        assertThat(CalendarRules.validate("Урок", null, END, START).hasErrors()).isTrue();
        assertThat(CalendarRules.validate(" ", null, START, null).hasErrors()).isTrue();
        assertThat(CalendarRules.validate("Урок", "x".repeat(CalendarRules.MAX_DESCRIPTION + 1), START, null).hasErrors())
                .isTrue();
        assertThat(CalendarRules.normalizeText("  ")).isNull();
        assertThat(CalendarRules.normalizeText(" текст ")).isEqualTo("текст");
    }

    private static Lesson lesson(LessonAudience audience, List<UUID> attendees, Instant start, Instant end) {
        UUID id = UUID.fromString("0192f3c1-0000-7000-8000-00000000000a");
        return new Lesson(id, UUID.randomUUID(), UUID.randomUUID(), null, null, "Дроби", null, start, end, audience,
                attendees, UUID.randomUUID(), 0, START, START);
    }
}
