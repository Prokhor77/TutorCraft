package com.tutorcraft.core.assessment.assignment.application;

import com.tutorcraft.core.assessment.assignment.domain.Feedback;
import com.tutorcraft.core.assessment.assignment.domain.Submission;
import com.tutorcraft.core.files.FilesApi;
import com.tutorcraft.core.files.FilesApi.FileRef;
import com.tutorcraft.core.identity.UsersApi;
import com.tutorcraft.core.identity.UsersApi.UserRef;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Сборка SubmissionView: файлы с pre-signed URL, отзыв, история попыток; видимость оценки по роли зрителя. */
@Component
class SubmissionViews {

    private static final String READY = "ready";

    enum Perspective { STUDENT, STAFF }

    private final SubmissionRepository submissions;
    private final FilesApi files;
    private final UsersApi users;

    SubmissionViews(SubmissionRepository submissions, FilesApi files, UsersApi users) {
        this.submissions = submissions;
        this.files = files;
        this.users = users;
    }

    SubmissionView build(AssignmentItem assignment, Submission submission, Instant dueAt, Perspective perspective) {
        Optional<Feedback> feedback = submissions.latestFeedback(submission.tenantId(), submission.id());
        Map<UUID, UserRef> people = users.findAll(submission.tenantId(), peopleOf(submission, feedback));
        boolean scoresVisible = perspective == Perspective.STAFF
                || feedback.map(f -> f.publishedAt() != null).orElse(false);
        return new SubmissionView(submission.id(), submission.itemId(), submission.authorId(),
                nameOf(people, submission.authorId()), submission.attemptNo(), submission.status().key(), submission.text(),
                fileMetas(submission.tenantId(), submission.fileIds()), submission.submittedAt(),
                submission.dueAt() != null ? submission.dueAt() : dueAt, submission.late(),
                grade(assignment, submission, feedback, people, perspective),
                history(submission, scoresVisible), submission.version());
    }

    List<SubmissionView.FileMeta> fileMetas(UUID tenantId, Collection<UUID> fileIds) {
        if (fileIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, FileRef> found = files.findAll(tenantId, fileIds).stream()
                .collect(Collectors.toMap(FileRef::id, file -> file));
        return fileIds.stream().map(found::get).filter(Objects::nonNull).map(this::fileMeta).toList();
    }

    private SubmissionView.FileMeta fileMeta(FileRef file) {
        String url = READY.equals(file.status()) ? files.downloadUrl(file) : null;
        return new SubmissionView.FileMeta(file.id(), file.name(), file.size(), file.mime(), file.status(), url);
    }

    private SubmissionView.Grade grade(AssignmentItem assignment, Submission submission, Optional<Feedback> feedback,
                                       Map<UUID, UserRef> people, Perspective perspective) {
        if (feedback.isEmpty()) {
            return null;
        }
        Feedback current = feedback.get();
        boolean published = current.publishedAt() != null;
        if (perspective == Perspective.STUDENT && !current.visibleToStudent(submission.status())) {
            return null;
        }
        BigDecimal score = perspective == Perspective.STAFF || published ? current.score() : null;
        return new SubmissionView.Grade(score, assignment.settings().maxScore(), published, current.text(),
                fileMetas(submission.tenantId(), current.fileIds()), current.createdAt(), nameOf(people, current.graderId()));
    }

    private List<SubmissionView.HistoryEntry> history(Submission submission, boolean scoresVisible) {
        List<Submission> attempts = submissions.attempts(submission.tenantId(), submission.itemId(), submission.ownerKey());
        Map<UUID, Feedback> feedbacks = scoresVisible
                ? submissions.latestFeedbacks(submission.tenantId(), attempts.stream().map(Submission::id).toList())
                : Map.of();
        return attempts.stream()
                .map(attempt -> new SubmissionView.HistoryEntry(attempt.attemptNo(), attempt.status().key(),
                        attempt.submittedAt(), Optional.ofNullable(feedbacks.get(attempt.id())).map(Feedback::score).orElse(null)))
                .toList();
    }

    private static Set<UUID> peopleOf(Submission submission, Optional<Feedback> feedback) {
        Set<UUID> ids = new HashSet<>();
        ids.add(submission.authorId());
        feedback.ifPresent(f -> ids.add(f.graderId()));
        return ids;
    }

    static String nameOf(Map<UUID, UserRef> people, UUID userId) {
        UserRef user = people.get(userId);
        return user == null ? null : user.displayName();
    }
}
