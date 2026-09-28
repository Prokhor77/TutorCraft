package com.tutorcraft.core.progress.application;

import com.tutorcraft.core.assessment.AssessmentEvents.AttemptFinished;
import com.tutorcraft.core.assessment.AssessmentEvents.SubmissionSubmitted;
import com.tutorcraft.core.communication.ForumEvents.PostCreated;
import com.tutorcraft.core.courses.CourseEvents.ItemViewed;
import com.tutorcraft.core.courses.CoursesApi;
import com.tutorcraft.core.courses.ItemRef;
import com.tutorcraft.core.gradebook.GradebookEvents.GradeChanged;
import com.tutorcraft.core.progress.domain.CompletionTrigger;
import com.tutorcraft.core.progress.domain.ProgressMath;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Автоматическое выполнение по событиям модулей (FR-PROG-01): просмотр, сдача, завершение попытки,
 * оценка (с учётом только опубликованных), пост в форуме. Выполняется в транзакции публикатора (или своей).
 */
@Component
class ProgressEventListener {

    private static final String PASS_PERCENT = "passPercent";
    private static final double PERCENT = 100.0;

    private final CompletionTracker tracker;
    private final CompletionRepository completions;
    private final CourseCompletionEvaluator courseCompletion;
    private final CoursesApi courses;
    private final Clock clock;

    ProgressEventListener(CompletionTracker tracker, CompletionRepository completions,
                          CourseCompletionEvaluator courseCompletion, CoursesApi courses, Clock clock) {
        this.tracker = tracker;
        this.completions = completions;
        this.courseCompletion = courseCompletion;
        this.courses = courses;
        this.clock = clock;
    }

    @EventListener
    @Transactional
    public void onItemViewed(ItemViewed event) {
        item(event.tenantId(), event.itemId()).ifPresent(item -> {
            completions.recordView(event.tenantId(), event.courseId(), event.itemId(), event.userId(), clock.instant());
            tracker.update(item, event.userId(), state -> state.withTrigger(CompletionTrigger.VIEWED, true));
        });
    }

    @EventListener
    @Transactional
    public void onSubmission(SubmissionSubmitted event) {
        reach(event.tenantId(), event.itemId(), event.userId(), CompletionTrigger.SUBMITTED);
    }

    @EventListener
    @Transactional
    public void onAttemptFinished(AttemptFinished event) {
        reach(event.tenantId(), event.itemId(), event.userId(), CompletionTrigger.SUBMITTED);
    }

    @EventListener
    @Transactional
    public void onPostCreated(PostCreated event) {
        reach(event.tenantId(), event.itemId(), event.authorId(), CompletionTrigger.POSTED);
    }

    @EventListener
    @Transactional
    public void onGradeChanged(GradeChanged event) {
        if (event.sourceItemId() != null) {
            item(event.tenantId(), event.sourceItemId()).ifPresent(item -> applyGrade(item, event));
        }
        courseCompletion.evaluate(event.tenantId(), event.courseId(), event.userId());
    }

    private void applyGrade(ItemRef item, GradeChanged event) {
        boolean graded = event.published() && event.score() != null;
        Double percent = graded && event.maxScore() != null && event.maxScore().signum() > 0
                ? event.score().doubleValue() * PERCENT / event.maxScore().doubleValue() : null;
        boolean passed = graded && ProgressMath.passed(percent, passPercent(item));
        tracker.update(item, event.userId(), state -> state.withTrigger(CompletionTrigger.GRADED, graded)
                .withTrigger(CompletionTrigger.PASSED, passed));
    }

    private void reach(UUID tenantId, UUID itemId, UUID userId, CompletionTrigger trigger) {
        item(tenantId, itemId).ifPresent(item -> tracker.update(item, userId, state -> state.withTrigger(trigger, true)));
    }

    private Optional<ItemRef> item(UUID tenantId, UUID itemId) {
        return Optional.ofNullable(courses.findItems(tenantId, List.of(itemId)).get(itemId));
    }

    private static Double passPercent(ItemRef item) {
        Object value = item.settings() == null ? null : item.settings().get(PASS_PERCENT);
        return value instanceof Number number ? number.doubleValue() : null;
    }
}
