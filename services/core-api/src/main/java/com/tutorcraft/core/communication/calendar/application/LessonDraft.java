package com.tutorcraft.core.communication.calendar.application;

import com.tutorcraft.core.communication.calendar.domain.LessonAudience;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Данные занятия от репетитора (создание и полная замена). attendeeIds пуст — занятие для всего курса. */
public record LessonDraft(String title, String description, Instant startsAt, Instant endsAt, UUID moduleId, UUID itemId,
                          List<UUID> attendeeIds) {

    public LessonDraft {
        attendeeIds = attendeeIds == null ? List.of() : attendeeIds.stream().distinct().toList();
    }

    LessonAudience audience() {
        return attendeeIds.isEmpty() ? LessonAudience.COURSE : LessonAudience.STUDENTS;
    }
}
