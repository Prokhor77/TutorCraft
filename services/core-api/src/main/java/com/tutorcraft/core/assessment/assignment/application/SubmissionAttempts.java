package com.tutorcraft.core.assessment.assignment.application;

import com.tutorcraft.core.assessment.assignment.application.StudentSubmissionService.StudentContext;
import com.tutorcraft.core.assessment.assignment.domain.Submission;
import com.tutorcraft.core.assessment.assignment.domain.SubmissionRules;
import com.tutorcraft.core.enrollment.EnrollmentApi;
import com.tutorcraft.core.shared.api.IfMatch;
import com.tutorcraft.core.shared.domain.ConflictException;
import com.tutorcraft.core.shared.domain.Ids;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Создание попыток и сохранение изменений сдачи. Вызывается внутри транзакции use case. */
@Component
class SubmissionAttempts {

    static final int FIRST_ATTEMPT = 1;

    private final SubmissionRepository submissions;
    private final EnrollmentApi enrollment;
    private final Clock clock;

    SubmissionAttempts(SubmissionRepository submissions, EnrollmentApi enrollment, Clock clock) {
        this.submissions = submissions;
        this.enrollment = enrollment;
        this.clock = clock;
    }

    /** Создаёт попытку (при гонке возвращает созданную параллельно); basedOn — копируется содержимое прошлой. */
    Submission create(StudentContext context, int attemptNo, Submission basedOn) {
        Instant now = clock.instant();
        Submission draft = Submission.newDraft(Ids.newId(), context.tenantId(), context.assignment().courseId(),
                context.assignment().itemId(), context.userId(), context.submissionGroupId(), attemptNo, now);
        if (basedOn != null) {
            draft = draft.withContent(basedOn.text(), basedOn.fileIds(), now);
        }
        if (!submissions.insert(draft)) {
            return submissions.findLatest(context.tenantId(), context.assignment().itemId(), context.ownerKey())
                    .orElseThrow(() -> new ConflictException("submission.concurrent_update", "Submission is being created"));
        }
        submissions.replaceFiles(draft.tenantId(), draft.id(), draft.fileIds());
        submissions.addMembers(draft.tenantId(), draft.id(), initialMembers(context, basedOn));
        return draft;
    }

    Submission ensureMember(Submission submission, UUID userId) {
        submissions.addMembers(submission.tenantId(), submission.id(), List.of(userId));
        return submission;
    }

    /** Текущая попытка под блокировкой; после возврата на доработку начинается новая (с копией содержимого). */
    Submission workingAttempt(StudentContext context) {
        Submission current = submissions.lockLatest(context.tenantId(), context.assignment().itemId(), context.ownerKey())
                .orElseGet(() -> create(context, FIRST_ATTEMPT, null));
        ensureMember(current, context.userId());
        if (!SubmissionRules.needsNewAttempt(current)) {
            return current;
        }
        SubmissionRules.requireAttemptAvailable(context.assignment().settings(), current.attemptNo());
        submissions.markSuperseded(current.tenantId(), current.id());
        return create(context, current.attemptNo() + 1, current);
    }

    void save(Submission updated, long expectedVersion) {
        if (!submissions.update(updated, expectedVersion)) {
            throw new ConflictException(IfMatch.VERSION_CONFLICT_CODE, "Submission was modified concurrently");
        }
        submissions.replaceFiles(updated.tenantId(), updated.id(), updated.fileIds());
    }

    private Set<UUID> initialMembers(StudentContext context, Submission basedOn) {
        Set<UUID> members = new HashSet<>();
        members.add(context.userId());
        if (basedOn != null) {
            members.addAll(submissions.members(basedOn.tenantId(), basedOn.id()));
        }
        if (context.submissionGroupId() != null) {
            members.addAll(enrollment.membersOfGroups(context.tenantId(), context.assignment().courseId(),
                    Set.of(context.submissionGroupId())));
        }
        return members;
    }
}
