package com.tutorcraft.core.gradebook.application;

import com.tutorcraft.core.access.domain.CourseRole;
import com.tutorcraft.core.assessment.AssessmentEvents.AttemptFinished;
import com.tutorcraft.core.assessment.AssessmentEvents.EssayGraded;
import com.tutorcraft.core.assessment.AssessmentEvents.SubmissionGraded;
import com.tutorcraft.core.assessment.AssessmentEvents.SubmissionSubmitted;
import com.tutorcraft.core.communication.NotificationsApi;
import com.tutorcraft.core.enrollment.EnrollmentApi;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * AC-8: при изменении очереди проверки (сдача, оценка сдачи, завершение попытки с эссе, оценка эссе) проверяющим курса
 * отправляется счётчик {@code grading_queue} с их текущим числом работ. После фиксации исходной транзакции и в своей
 * транзакции: сбой счётчика не откатывает сдачу/оценку, а значение отражает уже сохранённое состояние.
 */
@Component
class GradingQueueCounter {

    private static final Logger log = LoggerFactory.getLogger(GradingQueueCounter.class);
    private static final Set<CourseRole> GRADER_ROLES = Set.of(CourseRole.TEACHER, CourseRole.ASSISTANT);

    private final GradingQueueService queue;
    private final EnrollmentApi enrollment;
    private final NotificationsApi notifications;

    GradingQueueCounter(GradingQueueService queue, EnrollmentApi enrollment, NotificationsApi notifications) {
        this.queue = queue;
        this.enrollment = enrollment;
        this.notifications = notifications;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSubmitted(SubmissionSubmitted event) {
        refresh(event.tenantId(), event.courseId());
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSubmissionGraded(SubmissionGraded event) {
        refresh(event.tenantId(), event.courseId());
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAttemptFinished(AttemptFinished event) {
        refresh(event.tenantId(), event.courseId());
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onEssayGraded(EssayGraded event) {
        refresh(event.tenantId(), event.courseId());
    }

    private void refresh(UUID tenantId, UUID courseId) {
        enrollment.activeMembers(tenantId, courseId, GRADER_ROLES).stream()
                .map(EnrollmentApi.Member::userId)
                .distinct()
                .forEach(graderId -> push(tenantId, graderId));
    }

    private void push(UUID tenantId, UUID graderId) {
        int pending = queue.pendingCount(tenantId, graderId);
        notifications.pushCounter(tenantId, graderId, NotificationsApi.GRADING_QUEUE_COUNTER, pending);
        log.debug("Grading queue counter for user {}: {}", graderId, pending);
    }
}
