package com.tutorcraft.core.gradebook.spi;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Источник элементов единой очереди проверки (FR-GRADE-06): сдачи заданий (C), эссе в тестах (D). */
public interface GradingQueueSource {

    List<QueueEntry> pending(UUID tenantId, Collection<UUID> courseIds);

    record QueueEntry(String kind, String id, UUID courseId, UUID itemId, String itemTitle, UUID userId,
                      Instant submittedAt, Instant dueAt, boolean late) {

        public static final String SUBMISSION = "submission";
        public static final String ESSAY = "essay";
    }
}
