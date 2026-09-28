package com.tutorcraft.core.assessment.assignment.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.assessment.assignment.application.SubmissionViews.Perspective;
import com.tutorcraft.core.assessment.assignment.domain.AssignmentErrors;
import com.tutorcraft.core.assessment.assignment.domain.EffectiveDeadlines;
import com.tutorcraft.core.assessment.assignment.domain.OwnerKeys;
import com.tutorcraft.core.assessment.assignment.domain.Submission;
import com.tutorcraft.core.assessment.assignment.domain.SubmissionRules;
import com.tutorcraft.core.courses.CoursesApi;
import com.tutorcraft.core.enrollment.EnrollmentApi;
import com.tutorcraft.core.shared.domain.BusinessRuleException;
import com.tutorcraft.core.shared.domain.NotFoundException;
import com.tutorcraft.core.shared.idempotency.IdempotencyService;
import com.tutorcraft.core.shared.idempotency.IdempotencyService.IdempotencyScope;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Сдача задания студентом (FR-ASSIGN-04, UX-03, AC-3): черновик с автосохранением и идемпотентная отправка. */
@Service
public class StudentSubmissionService {

    static final String SUBMIT_OPERATION = "submission.submit";
    private static final Logger log = LoggerFactory.getLogger(StudentSubmissionService.class);

    private final CurrentUserProvider currentUser;
    private final AccessService access;
    private final AssignmentItems items;
    private final CoursesApi courses;
    private final EnrollmentApi enrollment;
    private final SubmissionRepository submissions;
    private final ItemOverrideRepository overrides;
    private final SubmissionAttempts attempts;
    private final SubmissionContent content;
    private final SubmissionViews views;
    private final SubmittedWorkNotifier submittedNotifier;
    private final IdempotencyService idempotency;
    private final Clock clock;

    StudentSubmissionService(CurrentUserProvider currentUser, AccessService access, AssignmentItems items,
                             CoursesApi courses, EnrollmentApi enrollment, SubmissionRepository submissions,
                             ItemOverrideRepository overrides, SubmissionAttempts attempts, SubmissionContent content,
                             SubmissionViews views, SubmittedWorkNotifier submittedNotifier,
                             IdempotencyService idempotency, Clock clock) {
        this.currentUser = currentUser;
        this.access = access;
        this.items = items;
        this.courses = courses;
        this.enrollment = enrollment;
        this.submissions = submissions;
        this.overrides = overrides;
        this.attempts = attempts;
        this.content = content;
        this.views = views;
        this.submittedNotifier = submittedNotifier;
        this.idempotency = idempotency;
        this.clock = clock;
    }

    /** Текущая попытка; при первом обращении создаётся черновик. */
    @Transactional
    public SubmissionView mySubmission(UUID itemId) {
        StudentContext context = context(itemId);
        Submission submission = submissions.findLatest(context.tenantId(), itemId, context.ownerKey())
                .map(existing -> attempts.ensureMember(existing, context.userId()))
                .orElseGet(() -> attempts.create(context, SubmissionAttempts.FIRST_ATTEMPT, null));
        return view(context, submission);
    }

    /** Автосохранение черновика; отсутствующее поле запроса не меняет сохранённое значение. */
    @Transactional
    public SubmissionView saveDraft(UUID itemId, DraftCommand command) {
        StudentContext context = context(itemId);
        Submission current = attempts.workingAttempt(context);
        SubmissionRules.requireEditable(current, context.assignment().settings());
        EffectiveDeadlines deadlines = deadlines(context);
        Instant now = clock.instant();
        SubmissionRules.requireWindowOpen(deadlines, now);
        Submission updated = content.apply(context, current, command, now);
        boolean autoSubmit = !context.assignment().settings().requireSubmitButton() && updated.hasContent();
        if (autoSubmit) {
            updated = updated.submitted(now, deadlines.dueAt(), SubmissionRules.isLate(deadlines, now));
        }
        attempts.save(updated, current.version());
        if (autoSubmit && !current.status().awaitsGrading()) {
            submittedNotifier.submitted(context.assignment(), updated);
        }
        return view(context, reload(updated));
    }

    /** Отправка на проверку (AC-3): повтор с тем же Idempotency-Key не создаёт вторую сдачу и не меняет время сдачи. */
    @Transactional
    public SubmissionView submit(UUID itemId, String idempotencyKey) {
        CurrentUser user = currentUser.require();
        UUID submissionId = idempotency.execute(new IdempotencyScope(user.tenantId(), user.userId(), SUBMIT_OPERATION),
                idempotencyKey, Map.of("itemId", itemId), UUID.class, () -> doSubmit(itemId).id());
        StudentContext context = context(itemId);
        Submission submission = submissions.find(context.tenantId(), submissionId)
                .orElseThrow(() -> new NotFoundException(AssignmentErrors.SUBMISSION_NOT_FOUND, "Submission not found"));
        return view(context, submission);
    }

    private Submission doSubmit(UUID itemId) {
        StudentContext context = context(itemId);
        Submission current = attempts.workingAttempt(context);
        SubmissionRules.requireSubmittable(current, context.assignment().settings());
        EffectiveDeadlines deadlines = deadlines(context);
        Instant now = clock.instant();
        SubmissionRules.requireWindowOpen(deadlines, now);
        Submission submitted = current.submitted(now, deadlines.dueAt(), SubmissionRules.isLate(deadlines, now));
        attempts.save(submitted, current.version());
        submittedNotifier.submitted(context.assignment(), submitted);
        log.info("Submission {} submitted for item {} (late={})", submitted.id(), itemId, submitted.late());
        return submitted;
    }

    private StudentContext context(UUID itemId) {
        CurrentUser user = currentUser.require();
        AssignmentItem assignment = items.require(user.tenantId(), itemId);
        access.require(Permission.SUBMISSION_SUBMIT, AccessContext.course(assignment.courseId()));
        if (!courses.isVisibleToLearners(user.tenantId(), assignment.item())) {
            throw new NotFoundException(AssignmentErrors.NOT_FOUND, "Assignment not found");
        }
        Set<UUID> groupIds = enrollment.groupIds(user.tenantId(), assignment.courseId(), user.userId());
        UUID submissionGroup = assignment.settings().groupSubmission()
                ? groupIds.stream().min(Comparator.naturalOrder()).orElse(null)
                : null;
        String ownerKey = submissionGroup == null ? OwnerKeys.user(user.userId()) : OwnerKeys.group(submissionGroup);
        return new StudentContext(user.tenantId(), user.userId(), assignment, groupIds, submissionGroup, ownerKey);
    }

    private EffectiveDeadlines deadlines(StudentContext context) {
        return EffectiveDeadlines.of(context.assignment().settings(),
                overrides.applicable(context.tenantId(), context.assignment().itemId(), context.userId(), context.groupIds()));
    }

    private Submission reload(Submission submission) {
        return submissions.find(submission.tenantId(), submission.id())
                .orElseThrow(() -> new BusinessRuleException(AssignmentErrors.SUBMISSION_NOT_FOUND, "Submission disappeared"));
    }

    private SubmissionView view(StudentContext context, Submission submission) {
        return views.build(context.assignment(), submission, deadlines(context).dueAt(), Perspective.STUDENT);
    }

    /** Черновик: text — BlockDoc, fileIds — файлы (готовые, загруженные студентом). null — не менять. */
    public record DraftCommand(Map<String, Object> text, List<UUID> fileIds) {
    }

    /** Студент, задание и владелец попыток (сам студент или его группа при групповой сдаче). */
    record StudentContext(UUID tenantId, UUID userId, AssignmentItem assignment, Set<UUID> groupIds, UUID submissionGroupId,
                          String ownerKey) {
    }
}
