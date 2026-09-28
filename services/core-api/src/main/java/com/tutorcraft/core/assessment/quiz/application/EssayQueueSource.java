package com.tutorcraft.core.assessment.quiz.application;

import com.tutorcraft.core.assessment.quiz.application.AttemptRepository.PendingEssay;
import com.tutorcraft.core.courses.CoursesApi;
import com.tutorcraft.core.courses.ItemRef;
import com.tutorcraft.core.gradebook.spi.GradingQueueSource;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Эссе завершённых попыток, ожидающие ручной проверки, в единой очереди (FR-GRADE-06). id = {@code attemptId:slot}. */
@Component
class EssayQueueSource implements GradingQueueSource {

    private static final String ID_SEPARATOR = ":";

    private final AttemptRepository attempts;
    private final CoursesApi courses;

    EssayQueueSource(AttemptRepository attempts, CoursesApi courses) {
        this.attempts = attempts;
        this.courses = courses;
    }

    @Override
    public List<QueueEntry> pending(UUID tenantId, Collection<UUID> courseIds) {
        if (courseIds.isEmpty()) {
            return List.of();
        }
        List<PendingEssay> essays = attempts.pendingEssays(tenantId, courseIds);
        Map<UUID, ItemRef> items = courses.findItems(tenantId, essays.stream().map(PendingEssay::itemId).collect(Collectors.toSet()));
        return essays.stream().filter(essay -> items.containsKey(essay.itemId()))
                .map(essay -> entry(essay, items.get(essay.itemId())))
                .toList();
    }

    private static QueueEntry entry(PendingEssay essay, ItemRef item) {
        return new QueueEntry(QueueEntry.ESSAY, essay.attemptId() + ID_SEPARATOR + essay.slot(), essay.courseId(), essay.itemId(),
                item.title(), essay.userId(), essay.finishedAt(), item.dueAt(), false);
    }
}
