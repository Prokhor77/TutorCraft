package com.tutorcraft.core.assessment.assignment.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.assessment.assignment.application.BlockDocInput.SanitizedText;
import com.tutorcraft.core.assessment.assignment.application.SubmissionViews.Perspective;
import com.tutorcraft.core.assessment.assignment.domain.AssignmentErrors;
import com.tutorcraft.core.assessment.assignment.domain.Feedback;
import com.tutorcraft.core.assessment.assignment.domain.Submission;
import com.tutorcraft.core.assessment.assignment.domain.SubmissionStatus;
import com.tutorcraft.core.audit.AuditLog;
import com.tutorcraft.core.audit.AuditRecord;
import com.tutorcraft.core.files.FilesApi;
import com.tutorcraft.core.files.FilesApi.FileRef;
import com.tutorcraft.core.gradebook.GradebookApi;
import com.tutorcraft.core.gradebook.GradebookApi.GradeUpdate;
import com.tutorcraft.core.shared.api.IfMatch;
import com.tutorcraft.core.shared.domain.BusinessRuleException;
import com.tutorcraft.core.shared.domain.ConflictException;
import com.tutorcraft.core.shared.domain.Ids;
import com.tutorcraft.core.shared.domain.ValidationException;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Оценка и отзыв (FR-ASSIGN-06), возврат на доработку, публикация оценок (FR-ASSIGN-07). */
@Service
public class GradingService {

    static final String FEEDBACK_OWNER_TYPE = "feedback";
    private static final int MAX_FEEDBACK_FILES = 20;
    private static final int SCORE_SCALE = 2;
    private static final String SCORE_FIELD = "score";
    private static final String FEEDBACK_FIELD = "feedback";
    private static final String FEEDBACK_FILES_FIELD = "feedbackFileIds";

    private final CurrentUserProvider currentUser;
    private final AccessService access;
    private final AssignmentItems items;
    private final SubmissionRepository submissions;
    private final SubmissionAccess submissionAccess;
    private final SubmissionViews views;
    private final BlockDocInput blockDocs;
    private final FilesApi files;
    private final GradebookApi gradebook;
    private final AuditLog audit;
    private final Clock clock;

    GradingService(CurrentUserProvider currentUser, AccessService access, AssignmentItems items,
                   SubmissionRepository submissions, SubmissionAccess submissionAccess, SubmissionViews views,
                   BlockDocInput blockDocs, FilesApi files, GradebookApi gradebook, AuditLog audit, Clock clock) {
        this.currentUser = currentUser;
        this.access = access;
        this.items = items;
        this.submissions = submissions;
        this.submissionAccess = submissionAccess;
        this.views = views;
        this.blockDocs = blockDocs;
        this.files = files;
        this.gradebook = gradebook;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public SubmissionView grade(UUID submissionId, GradeCommand command) {
        CurrentUser user = currentUser.require();
        Submission submission = submissionAccess.require(user.tenantId(), submissionId);
        AssignmentItem assignment = items.require(user.tenantId(), submission.itemId());
        submissionAccess.requireStaff(user, assignment, submission, Permission.SUBMISSION_GRADE);
        Submission locked = lockCurrent(submission);
        validateScore(command.score(), assignment.settings().maxScore());
        SanitizedText text = blockDocs.sanitize(command.feedback(), FEEDBACK_FIELD);
        List<FileRef> feedbackFiles = feedbackFiles(user, command.feedbackFileIds(), text);
        Instant now = clock.instant();
        boolean publish = assignment.settings().autoPublishGrades() || alreadyPublished(assignment, locked);
        Feedback feedback = new Feedback(Ids.newId(), user.tenantId(), locked.id(), user.userId(), text.doc(),
                command.feedbackFileIds() == null ? List.of() : command.feedbackFileIds().stream().distinct().toList(),
                command.score(), command.returnForRevision(), publish ? now : null, now);
        submissions.insertFeedback(feedback);
        feedbackFiles.forEach(file -> files.link(user.tenantId(), file.id(), FEEDBACK_OWNER_TYPE, feedback.id()));
        Submission graded = locked.withStatus(nextStatus(locked, command), now);
        saveStatus(graded, locked.version());
        recordGrades(assignment, graded, command.score(), user.userId(), publish);
        audit.record(AuditRecord.of(user.tenantId(), user.userId(), "submission.graded", "submission", locked.id().toString())
                .withDiff(gradeDiff(command, graded.status())));
        return views.build(assignment, submissionAccess.require(user.tenantId(), locked.id()),
                assignment.settings().dueAt(), Perspective.STAFF);
    }

    /** «Опубликовать все оценки» задания (FR-ASSIGN-07). */
    @Transactional
    public int publishAll(UUID itemId) {
        CurrentUser user = currentUser.require();
        AssignmentItem assignment = items.require(user.tenantId(), itemId);
        access.require(Permission.GRADE_PUBLISH, AccessContext.course(assignment.courseId()));
        int published = gradebook.publishAll(user.tenantId(), itemId, user.userId());
        submissions.publishFeedback(user.tenantId(), itemId, clock.instant());
        audit.record(AuditRecord.of(user.tenantId(), user.userId(), "grades.published", "item", itemId.toString())
                .withDiff(Map.of("published", published)));
        return published;
    }

    private Submission lockCurrent(Submission submission) {
        Submission locked = submissions.lockLatest(submission.tenantId(), submission.itemId(), submission.ownerKey())
                .orElseThrow(() -> new BusinessRuleException(AssignmentErrors.NOT_LATEST_ATTEMPT, "Not the latest attempt"));
        if (!locked.id().equals(submission.id())) {
            throw new BusinessRuleException(AssignmentErrors.NOT_LATEST_ATTEMPT, "Only the latest attempt can be graded");
        }
        return locked;
    }

    private static void validateScore(BigDecimal score, BigDecimal maxScore) {
        if (score == null) {
            return;
        }
        boolean inRange = score.signum() >= 0 && score.compareTo(maxScore) <= 0;
        if (!inRange || score.stripTrailingZeros().scale() > SCORE_SCALE) {
            throw ValidationException.single(SCORE_FIELD, "out_of_range",
                    "Score must be between 0 and " + maxScore.toPlainString() + " with at most 2 decimals");
        }
    }

    private List<FileRef> feedbackFiles(CurrentUser user, List<UUID> requested, SanitizedText text) {
        Set<UUID> ids = new LinkedHashSet<>(requested == null ? List.of() : requested);
        if (ids.size() > MAX_FEEDBACK_FILES) {
            throw ValidationException.single(FEEDBACK_FILES_FIELD, "too_many_files", "Too many files");
        }
        ids.addAll(text.fileIds());
        List<FileRef> refs = files.requireAllReady(user.tenantId(), ids, FEEDBACK_FILES_FIELD);
        if (!refs.stream().allMatch(file -> user.userId().equals(file.uploadedBy()))) {
            throw ValidationException.single(FEEDBACK_FILES_FIELD, "file_not_owned", "You can attach only your own files");
        }
        return refs;
    }

    private boolean alreadyPublished(AssignmentItem assignment, Submission submission) {
        return gradebook.grade(submission.tenantId(), assignment.itemId(), submission.authorId())
                .map(GradebookApi.GradeView::published).orElse(false);
    }

    private static SubmissionStatus nextStatus(Submission submission, GradeCommand command) {
        if (command.returnForRevision()) {
            return SubmissionStatus.RETURNED;
        }
        return command.score() != null ? SubmissionStatus.GRADED : submission.status();
    }

    private void saveStatus(Submission graded, long expectedVersion) {
        if (!submissions.update(graded, expectedVersion)) {
            throw new ConflictException(IfMatch.VERSION_CONFLICT_CODE, "Submission was modified concurrently");
        }
    }

    /** Оценка пишется в журнал всем участникам попытки (групповая сдача — каждому члену группы). */
    private void recordGrades(AssignmentItem assignment, Submission submission, BigDecimal score, UUID graderId,
                              boolean publish) {
        if (score == null) {
            return;
        }
        gradebook.ensureGradeItem(submission.tenantId(), assignment.courseId(), assignment.itemId(), assignment.item().title(),
                assignment.settings().maxScore(), assignment.settings().gradeCategoryId());
        submissions.members(submission.tenantId(), submission.id()).forEach(memberId -> gradebook.recordGrade(
                new GradeUpdate(submission.tenantId(), assignment.courseId(), assignment.itemId(), memberId, score,
                        graderId, publish)));
    }

    private static Map<String, Object> gradeDiff(GradeCommand command, SubmissionStatus status) {
        Map<String, Object> diff = new HashMap<>();
        diff.put(SCORE_FIELD, command.score());
        diff.put("status", status.key());
        return diff;
    }

    /** score: null — без балла (только отзыв или возврат); feedback — BlockDoc. */
    public record GradeCommand(BigDecimal score, Map<String, Object> feedback, List<UUID> feedbackFileIds,
                               boolean returnForRevision) {
    }
}
