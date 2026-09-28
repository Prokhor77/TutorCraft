package com.tutorcraft.core.progress.application;

import com.tutorcraft.core.courses.CourseRef;
import com.tutorcraft.core.courses.CoursesApi;
import com.tutorcraft.core.gradebook.GradebookApi;
import com.tutorcraft.core.progress.ProgressEvents.CourseCompleted;
import com.tutorcraft.core.progress.domain.CourseCompletionPolicy;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Проверка завершения курса после каждого изменения выполнения или оценки (FR-PROG-05).
 * Дата завершения фиксируется один раз; событие {@link CourseCompleted} — только при первом завершении.
 */
@Component
public class CourseCompletionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(CourseCompletionEvaluator.class);

    private final CompletionRepository completions;
    private final CoursesApi courses;
    private final GradebookApi gradebook;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public CourseCompletionEvaluator(CompletionRepository completions, CoursesApi courses, GradebookApi gradebook,
                                     ApplicationEventPublisher events, Clock clock) {
        this.completions = completions;
        this.courses = courses;
        this.gradebook = gradebook;
        this.events = events;
        this.clock = clock;
    }

    public void evaluate(UUID tenantId, UUID courseId, UUID userId) {
        if (completions.courseCompletedAt(tenantId, courseId, userId).isPresent()) {
            return;
        }
        CourseRef course = courses.findCourse(tenantId, courseId).orElse(null);
        if (course == null) {
            return;
        }
        List<UUID> required = course.requiredItemIds() == null ? List.of() : course.requiredItemIds();
        Double minFinal = course.minFinalPercent();
        if (!CourseCompletionPolicy.hasRule(required, minFinal)) {
            return;
        }
        Set<UUID> completed = completions.completedItems(tenantId, courseId, userId);
        Double finalPercent = minFinal == null ? null
                : gradebook.finalPercent(tenantId, courseId, userId).map(BigDecimal::doubleValue).orElse(null);
        if (CourseCompletionPolicy.isComplete(required, minFinal, completed, finalPercent)
                && completions.markCourseCompleted(tenantId, courseId, userId, clock.instant())) {
            events.publishEvent(new CourseCompleted(tenantId, courseId, userId));
            log.info("Course {} completed by user {}", courseId, userId);
        }
    }
}
