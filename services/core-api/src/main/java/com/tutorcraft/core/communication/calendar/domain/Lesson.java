package com.tutorcraft.core.communication.calendar.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Занятие курса в календаре. attendeeIds пуст при audience = COURSE. */
public record Lesson(UUID id, UUID tenantId, UUID courseId, UUID moduleId, UUID itemId, String title, String description,
                     Instant startsAt, Instant endsAt, LessonAudience audience, List<UUID> attendeeIds, UUID createdBy,
                     long version, Instant createdAt, Instant updatedAt) {

    public Lesson {
        attendeeIds = audience == LessonAudience.COURSE || attendeeIds == null ? List.of() : List.copyOf(attendeeIds);
    }

    public Lesson withVersion(long newVersion) {
        return new Lesson(id, tenantId, courseId, moduleId, itemId, title, description, startsAt, endsAt, audience,
                attendeeIds, createdBy, newVersion, createdAt, updatedAt);
    }

    /** Занятие адресовано ученику: всему курсу или ему лично. */
    public boolean addressedTo(UUID userId) {
        return audience == LessonAudience.COURSE || attendeeIds.contains(userId);
    }

    /** Изменилось время — ученикам нужно сообщить о переносе. */
    public boolean rescheduledFrom(Lesson previous) {
        return !startsAt.equals(previous.startsAt()) || !Objects.equals(endsAt, previous.endsAt());
    }

    /** Ученики, которых касается занятие, среди активных учеников курса. */
    public List<UUID> recipients(Set<UUID> activeStudents) {
        return audience == LessonAudience.COURSE
                ? List.copyOf(activeStudents)
                : attendeeIds.stream().filter(activeStudents::contains).toList();
    }
}
