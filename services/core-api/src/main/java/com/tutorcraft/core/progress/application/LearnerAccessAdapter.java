package com.tutorcraft.core.progress.application;

import com.tutorcraft.core.courses.CoursesApi;
import com.tutorcraft.core.courses.ItemRef;
import com.tutorcraft.core.progress.LearnerAccess;
import com.tutorcraft.core.progress.domain.Evaluation;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Открыт ли элемент студенту: видимость (courses) + условия модулей и элемента (FR-PROG-02/03). */
@Component
class LearnerAccessAdapter implements LearnerAccess {

    private final CoursesApi courses;
    private final AvailabilityService availability;
    private final CompletionRepository completions;

    LearnerAccessAdapter(CoursesApi courses, AvailabilityService availability, CompletionRepository completions) {
        this.courses = courses;
        this.availability = availability;
        this.completions = completions;
    }

    @Override
    public Status statusOf(UUID tenantId, UUID userId, ItemRef item) {
        if (!courses.isVisibleToLearners(tenantId, item)) {
            return Status.HIDDEN;
        }
        Map<UUID, Evaluation> evaluations = availability.evaluate(tenantId, userId, item.courseId(),
                courses.modulesOfCourse(tenantId, item.courseId()), courses.itemsOfCourse(tenantId, item.courseId()),
                completions.completedItems(tenantId, item.courseId(), userId));
        Evaluation evaluation = evaluations.getOrDefault(item.id(), Evaluation.OPEN);
        if (evaluation.available()) {
            return Status.OPEN;
        }
        return evaluation.showWhenLocked() ? Status.LOCKED : Status.HIDDEN;
    }
}
