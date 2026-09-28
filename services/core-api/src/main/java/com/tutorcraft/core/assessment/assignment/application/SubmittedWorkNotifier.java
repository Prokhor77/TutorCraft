package com.tutorcraft.core.assessment.assignment.application;

import com.tutorcraft.core.access.domain.CourseRole;
import com.tutorcraft.core.assessment.AssessmentEvents.SubmissionSubmitted;
import com.tutorcraft.core.assessment.assignment.domain.Submission;
import com.tutorcraft.core.communication.NotificationCategory;
import com.tutorcraft.core.communication.NotificationsApi;
import com.tutorcraft.core.communication.NotificationsApi.NotificationCommand;
import com.tutorcraft.core.enrollment.EnrollmentApi;
import com.tutorcraft.core.identity.UsersApi;
import com.tutorcraft.core.identity.UsersApi.UserRef;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * После отправки работы: доменное событие (вебхуки, выполнение) и уведомление проверяющим курса
 * (категория submission_received; по умолчанию только web — без писем на каждую сдачу).
 */
@Component
class SubmittedWorkNotifier {

    static final String MESSAGE_CODE = "assignment.submission_received";
    private static final String LINK_PREFIX = "/submissions/";
    private static final String DEDUPE_PREFIX = "submission_received:";
    private static final Set<CourseRole> GRADER_ROLES = Set.of(CourseRole.TEACHER, CourseRole.ASSISTANT);

    private final ApplicationEventPublisher events;
    private final EnrollmentApi enrollment;
    private final UsersApi users;
    private final NotificationsApi notifications;

    SubmittedWorkNotifier(ApplicationEventPublisher events, EnrollmentApi enrollment, UsersApi users,
                          NotificationsApi notifications) {
        this.events = events;
        this.enrollment = enrollment;
        this.users = users;
        this.notifications = notifications;
    }

    void submitted(AssignmentItem assignment, Submission submission) {
        events.publishEvent(new SubmissionSubmitted(submission.tenantId(), submission.courseId(), submission.itemId(),
                submission.authorId(), submission.id(), submission.late()));
        List<UUID> graders = enrollment.activeMembers(submission.tenantId(), submission.courseId(), GRADER_ROLES).stream()
                .map(EnrollmentApi.Member::userId)
                .toList();
        if (graders.isEmpty()) {
            return;
        }
        String studentName = users.find(submission.tenantId(), submission.authorId()).map(UserRef::displayName).orElse("");
        notifications.notify(NotificationCommand.of(submission.tenantId(), graders, NotificationCategory.SUBMISSION_RECEIVED,
                MESSAGE_CODE, List.<Object>of(studentName, assignment.item().title()), LINK_PREFIX + submission.id(),
                DEDUPE_PREFIX + submission.id()));
    }
}
