package com.tutorcraft.core.integrations.application;

import com.tutorcraft.core.assessment.AssessmentEvents.SubmissionSubmitted;
import com.tutorcraft.core.enrollment.EnrollmentEvents.EnrollmentChanged;
import com.tutorcraft.core.gradebook.GradebookEvents.GradeChanged;
import com.tutorcraft.core.integrations.domain.WebhookEvent;
import com.tutorcraft.core.progress.ProgressEvents.CourseCompleted;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Преобразует доменные события модулей в события вебхуков (FR-INTEG-02). Слушатели синхронные —
 * постановка в очередь происходит в транзакции публикующей операции.
 */
@Component
public class DomainEventWebhookListener {

    private static final String COURSE_ID = "courseId";
    private static final String USER_ID = "userId";

    private final WebhookEnqueuer enqueuer;

    public DomainEventWebhookListener(WebhookEnqueuer enqueuer) {
        this.enqueuer = enqueuer;
    }

    @EventListener
    public void onEnrollment(EnrollmentChanged event) {
        if (!event.created()) {
            return;
        }
        enqueuer.enqueue(event.tenantId(), WebhookEvent.ENROLLMENT_CREATED, new Data()
                .with(COURSE_ID, event.courseId()).with(USER_ID, event.userId())
                .with("role", event.roleKey()).with("method", event.method()).map());
    }

    @EventListener
    public void onSubmission(SubmissionSubmitted event) {
        enqueuer.enqueue(event.tenantId(), WebhookEvent.SUBMISSION_SUBMITTED, new Data()
                .with(COURSE_ID, event.courseId()).with("itemId", event.itemId()).with(USER_ID, event.userId())
                .with("submissionId", event.submissionId()).with("late", event.late()).map());
    }

    @EventListener
    public void onGrade(GradeChanged event) {
        if (!event.published()) {
            return;
        }
        enqueuer.enqueue(event.tenantId(), WebhookEvent.GRADE_PUBLISHED, new Data()
                .with(COURSE_ID, event.courseId()).with("itemId", event.sourceItemId()).with(USER_ID, event.userId())
                .with("score", event.score()).with("maxScore", event.maxScore()).map());
    }

    @EventListener
    public void onCourseCompleted(CourseCompleted event) {
        enqueuer.enqueue(event.tenantId(), WebhookEvent.COURSE_COMPLETED, new Data()
                .with(COURSE_ID, event.courseId()).with(USER_ID, event.userId()).map());
    }

    /** Данные события в стабильном порядке полей; null-значения допустимы. */
    private static final class Data {

        private final Map<String, Object> values = new LinkedHashMap<>();

        Data with(String key, Object value) {
            values.put(key, value);
            return this;
        }

        Map<String, Object> map() {
            return values;
        }
    }
}
