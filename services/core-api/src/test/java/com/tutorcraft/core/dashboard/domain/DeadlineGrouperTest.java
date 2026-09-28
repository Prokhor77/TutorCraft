package com.tutorcraft.core.dashboard.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.tutorcraft.core.dashboard.domain.DeadlineGrouper.Bucket;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** AC-9: группы «Просрочено / Сегодня / На этой неделе / Позже» в часовом поясе пользователя. */
class DeadlineGrouperTest {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");          // UTC+3
    private static final ZoneId VLADIVOSTOK = ZoneId.of("Asia/Vladivostok");  // UTC+10
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");     // UTC-4 (EDT)
    /** Воскресенье, 27.09.2026, 12:00 UTC (15:00 в Москве). */
    private static final Instant NOW = Instant.parse("2026-09-27T12:00:00Z");

    @Test
    void acceptanceCriterion9() {
        List<Instant> deadlines = List.of(
                NOW.minusSeconds(3600),                         // просрочено
                Instant.parse("2026-09-27T18:00:00Z"),          // сегодня (21:00 МСК)
                Instant.parse("2026-09-27T20:59:00Z"),          // сегодня (23:59 МСК)
                Instant.parse("2026-09-28T09:00:00Z"),          // пн
                Instant.parse("2026-09-29T09:00:00Z"),
                Instant.parse("2026-09-30T09:00:00Z"),
                Instant.parse("2026-10-01T09:00:00Z"),
                Instant.parse("2026-10-03T20:59:00Z"),          // сб 23:59 МСК — последний день недели
                Instant.parse("2026-10-03T21:00:00Z"));         // вс 00:00 МСК — позже

        Map<Bucket, List<Instant>> groups = DeadlineGrouper.group(deadlines, deadline -> deadline, NOW, MOSCOW);

        assertThat(groups.get(Bucket.OVERDUE)).hasSize(1);
        assertThat(groups.get(Bucket.TODAY)).hasSize(2);
        assertThat(groups.get(Bucket.THIS_WEEK)).hasSize(5);
        assertThat(groups.get(Bucket.LATER)).hasSize(1);
    }

    @Test
    void todayDependsOnUserTimezone() {
        Instant dueAt = Instant.parse("2026-09-27T15:00:00Z");

        assertThat(DeadlineGrouper.bucket(dueAt, NOW, MOSCOW)).isEqualTo(Bucket.TODAY);         // 18:00 27.09
        assertThat(DeadlineGrouper.bucket(dueAt, NOW, VLADIVOSTOK)).isEqualTo(Bucket.THIS_WEEK); // 01:00 28.09
        assertThat(DeadlineGrouper.bucket(dueAt, NOW, NEW_YORK)).isEqualTo(Bucket.TODAY);        // 11:00 27.09
    }

    @Test
    void justPassedDeadlineIsOverdueEvenIfSameDay() {
        assertThat(DeadlineGrouper.bucket(NOW.minusMillis(1), NOW, MOSCOW)).isEqualTo(Bucket.OVERDUE);
        assertThat(DeadlineGrouper.bucket(NOW, NOW, MOSCOW)).isEqualTo(Bucket.TODAY);
    }

    @Test
    void groupsAreSortedByDueDateAndAlwaysPresent() {
        Instant first = Instant.parse("2026-09-29T09:00:00Z");
        Instant second = Instant.parse("2026-09-28T09:00:00Z");

        Map<Bucket, List<Instant>> groups = DeadlineGrouper.group(List.of(first, second), deadline -> deadline, NOW, MOSCOW);

        assertThat(groups.get(Bucket.THIS_WEEK)).containsExactly(second, first);
        assertThat(groups.get(Bucket.OVERDUE)).isEmpty();
        assertThat(groups.get(Bucket.TODAY)).isEmpty();
        assertThat(groups.get(Bucket.LATER)).isEmpty();
    }
}
