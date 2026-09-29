package com.tutorcraft.core.communication.calendar.application;

import com.tutorcraft.core.communication.calendar.domain.Lesson;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Занятия курсов и их ученики. */
public interface LessonRepository {

    void insert(Lesson lesson);

    /** @return false — версия устарела (оптимистичная блокировка) */
    boolean update(Lesson lesson, long expectedVersion);

    void delete(UUID tenantId, UUID lessonId);

    Optional<Lesson> find(UUID tenantId, UUID courseId, UUID lessonId);

    /** Занятия перечисленных курсов, пересекающие [from, to). */
    List<Lesson> inCourses(UUID tenantId, Collection<UUID> courseIds, Instant from, Instant to);
}
