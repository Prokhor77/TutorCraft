package com.tutorcraft.core.assessment.assignment.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.assessment.assignment.application.SubmissionRepository.SubmissionFilter;
import com.tutorcraft.core.assessment.assignment.application.SubmissionViews.Perspective;
import com.tutorcraft.core.assessment.assignment.domain.Feedback;
import com.tutorcraft.core.assessment.assignment.domain.Submission;
import com.tutorcraft.core.assessment.assignment.domain.SubmissionStatus;
import com.tutorcraft.core.enrollment.EnrollmentApi;
import com.tutorcraft.core.identity.UsersApi;
import com.tutorcraft.core.identity.UsersApi.UserRef;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.api.PageResponse;
import com.tutorcraft.core.shared.domain.ValidationException;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Просмотр сдач проверяющим и студентом-владельцем (FR-ASSIGN-05, контракт §8). */
@Service
public class SubmissionReviewService {

    private static final String STATUS_FIELD = "status";
    private static final String NOT_GRADED = "not_graded";
    private static final String LATE = "late";

    private final CurrentUserProvider currentUser;
    private final AccessService access;
    private final AssignmentItems items;
    private final SubmissionRepository submissions;
    private final EnrollmentApi enrollment;
    private final UsersApi users;
    private final GraderScope scope;
    private final SubmissionViews views;
    private final SubmissionAccess submissionAccess;

    SubmissionReviewService(CurrentUserProvider currentUser, AccessService access, AssignmentItems items,
                            SubmissionRepository submissions, EnrollmentApi enrollment, UsersApi users, GraderScope scope,
                            SubmissionViews views, SubmissionAccess submissionAccess) {
        this.currentUser = currentUser;
        this.access = access;
        this.items = items;
        this.submissions = submissions;
        this.enrollment = enrollment;
        this.users = users;
        this.scope = scope;
        this.views = views;
        this.submissionAccess = submissionAccess;
    }

    /** Список текущих попыток. status: draft|submitted|submitted_late|graded|returned|not_graded|late. */
    @Transactional(readOnly = true)
    public PageResponse<SubmissionView.Summary> list(UUID itemId, String status, UUID groupId, PageQuery page) {
        CurrentUser user = currentUser.require();
        AssignmentItem assignment = items.require(user.tenantId(), itemId);
        access.require(Permission.SUBMISSION_VIEW_ALL, AccessContext.course(assignment.courseId()));
        SubmissionFilter filter = filter(status, memberRestriction(user, assignment, groupId));
        List<Submission> rows = submissions.listLatest(user.tenantId(), itemId, filter, page);
        PageResponse<Submission> result = page.toPage(rows, SubmissionReviewService::sortKey, Submission::id);
        return summaries(user.tenantId(), result);
    }

    /** Студент-участник видит свою сдачу; проверяющий — любую доступную ему. */
    @Transactional(readOnly = true)
    public SubmissionView get(UUID submissionId) {
        CurrentUser user = currentUser.require();
        Submission submission = submissionAccess.require(user.tenantId(), submissionId);
        AssignmentItem assignment = items.require(user.tenantId(), submission.itemId());
        if (submissions.isMember(user.tenantId(), submissionId, user.userId())) {
            access.require(Permission.SUBMISSION_SUBMIT, AccessContext.course(assignment.courseId()));
            return views.build(assignment, submission, assignment.settings().dueAt(), Perspective.STUDENT);
        }
        submissionAccess.requireStaff(user, assignment, submission, Permission.SUBMISSION_VIEW_ALL);
        return views.build(assignment, submission, assignment.settings().dueAt(), Perspective.STAFF);
    }

    private Set<UUID> memberRestriction(CurrentUser user, AssignmentItem assignment, UUID groupId) {
        Optional<Set<UUID>> allowed = scope.allowedStudents(user.tenantId(), assignment.courseId(), user.userId());
        if (groupId == null) {
            return allowed.orElse(null);
        }
        Set<UUID> groupMembers = new HashSet<>(enrollment.membersOfGroups(user.tenantId(), assignment.courseId(), Set.of(groupId)));
        allowed.ifPresent(groupMembers::retainAll);
        return groupMembers;
    }

    private static SubmissionFilter filter(String status, Set<UUID> memberIds) {
        if (status == null || status.isBlank()) {
            return new SubmissionFilter(Set.of(), false, memberIds);
        }
        return switch (status) {
            case NOT_GRADED -> new SubmissionFilter(Set.of(SubmissionStatus.SUBMITTED, SubmissionStatus.SUBMITTED_LATE), false, memberIds);
            case LATE -> new SubmissionFilter(Set.of(), true, memberIds);
            default -> new SubmissionFilter(Set.of(parseStatus(status)), false, memberIds);
        };
    }

    private static SubmissionStatus parseStatus(String status) {
        try {
            return SubmissionStatus.fromKey(status);
        } catch (IllegalArgumentException e) {
            throw ValidationException.single(STATUS_FIELD, "invalid", "Unknown submission status filter");
        }
    }

    private PageResponse<SubmissionView.Summary> summaries(UUID tenantId, PageResponse<Submission> page) {
        List<Submission> rows = page.items();
        Map<UUID, UserRef> authors = users.findAll(tenantId, rows.stream().map(Submission::authorId).toList());
        Map<UUID, Feedback> feedbacks = submissions.latestFeedbacks(tenantId, rows.stream().map(Submission::id).toList());
        return page.map(row -> new SubmissionView.Summary(row.id(), row.authorId(), SubmissionViews.nameOf(authors, row.authorId()),
                row.status().key(), row.submittedAt(), row.late(),
                Optional.ofNullable(feedbacks.get(row.id())).map(Feedback::score).orElse(null)));
    }

    private static Instant sortKey(Submission submission) {
        return submission.submittedAt() != null ? submission.submittedAt() : submission.createdAt();
    }
}
