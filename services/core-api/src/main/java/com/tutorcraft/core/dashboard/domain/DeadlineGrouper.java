package com.tutorcraft.core.dashboard.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Группировка дедлайнов «Моих задач» (FR-DASH-01, AC-9) в часовом поясе пользователя:
 * просрочено (срок прошёл) / сегодня (до конца текущих суток) / на этой неделе (следующие 6 календарных дней) / позже.
 * «Неделя» — скользящая: в воскресенье вечером ближайшие задачи не уходят в «позже».
 */
public final class DeadlineGrouper {

    public static final int WEEK_DAYS_AFTER_TODAY = 6;

    public enum Bucket { OVERDUE, TODAY, THIS_WEEK, LATER }

    private DeadlineGrouper() {
    }

    public static Bucket bucket(Instant dueAt, Instant now, ZoneId zone) {
        if (dueAt.isBefore(now)) {
            return Bucket.OVERDUE;
        }
        LocalDate today = now.atZone(zone).toLocalDate();
        LocalDate dueDate = dueAt.atZone(zone).toLocalDate();
        if (dueDate.equals(today)) {
            return Bucket.TODAY;
        }
        return dueDate.isAfter(today.plusDays(WEEK_DAYS_AFTER_TODAY)) ? Bucket.LATER : Bucket.THIS_WEEK;
    }

    /** Все группы присутствуют (возможно пустые); внутри группы — по возрастанию срока. */
    public static <T> Map<Bucket, List<T>> group(List<T> entries, Function<T, Instant> dueOf, Instant now, ZoneId zone) {
        Map<Bucket, List<T>> groups = new EnumMap<>(Bucket.class);
        for (Bucket bucket : Bucket.values()) {
            groups.put(bucket, new ArrayList<>());
        }
        entries.stream()
                .sorted(Comparator.comparing(dueOf))
                .forEach(entry -> groups.get(bucket(dueOf.apply(entry), now, zone)).add(entry));
        return groups;
    }
}
