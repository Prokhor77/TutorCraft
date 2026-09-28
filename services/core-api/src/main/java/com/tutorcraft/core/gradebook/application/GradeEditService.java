package com.tutorcraft.core.gradebook.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.CourseRole;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.audit.AuditLog;
import com.tutorcraft.core.audit.AuditRecord;
import com.tutorcraft.core.enrollment.EnrollmentApi;
import com.tutorcraft.core.gradebook.GradebookEvents.GradeChanged;
import com.tutorcraft.core.gradebook.application.GradebookViews.Cell;
import com.tutorcraft.core.gradebook.domain.Grade;
import com.tutorcraft.core.gradebook.domain.GradeColumn;
import com.tutorcraft.core.shared.api.IfMatch;
import com.tutorcraft.core.shared.domain.ConflictException;
import com.tutorcraft.core.shared.domain.NotFoundException;
import com.tutorcraft.core.shared.domain.ValidationException;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ручная правка оценок в журнале (FR-GRADE-04): ячейка, переопределение с блокировкой, история и аудит. */
@Service
public class GradeEditService {

    private static final String REASON_CELL = "cell";
    private static final String REASON_OVERRIDE = "override";
    private static final String SCORE_FIELD = "score";
    private static final int SCORE_SCALE = 2;

    private final CurrentUserProvider currentUser;
    private final AccessService access;
    private final EnrollmentApi enrollment;
    private final GradebookStructureRepository structure;
    private final GradeRepository grades;
    private final GradePublishedNotifier notifier;
    private final ApplicationEventPublisher events;
    private final AuditLog audit;
    private final Clock clock;

    GradeEditService(CurrentUserProvider currentUser, AccessService access, EnrollmentApi enrollment,
                     GradebookStructureRepository structure, GradeRepository grades, GradePublishedNotifier notifier,
                     ApplicationEventPublisher events, AuditLog audit, Clock clock) {
        this.currentUser = currentUser;
        this.access = access;
        this.enrollment = enrollment;
        this.structure = structure;
        this.grades = grades;
        this.notifier = notifier;
        this.events = events;
        this.audit = audit;
        this.clock = clock;
    }

    /** PUT cells: создаёт оценку при отсутствии. Для столбца элемента курса — переопределение. */
    @Transactional
    public Cell upsertCell(UUID courseId, UUID columnId, UUID userId, BigDecimal score) {
        CurrentUser user = currentUser.require();
        access.require(Permission.GRADE_EDIT, AccessContext.course(courseId));
        GradeColumn column = structure.column(user.tenantId(), columnId)
                .filter(found -> found.courseId().equals(courseId))
                .orElseThrow(() -> new NotFoundException(GradebookErrors.ITEM_NOT_FOUND, "Grade item not found"));
        requireStudent(user.tenantId(), courseId, userId);
        validateScore(score, column.maxScore());
        Instant now = clock.instant();
        Grade current = grades.lockOrCreate(user.tenantId(), columnId, userId, now);
        Grade next = column.sourceItemId() == null
                ? current.withManualScore(score, user.userId(), now)
                : current.withOverride(score, current.locked(), user.userId(), now);
        return save(user, column, current, next, REASON_CELL, now);
    }

    /** PATCH /grades/{id}: переопределение и блокировка (If-Match). */
    @Transactional
    public Cell override(UUID gradeId, BigDecimal score, Boolean locked, long expectedVersion) {
        CurrentUser user = currentUser.require();
        Grade current = grades.lock(user.tenantId(), gradeId)
                .orElseThrow(() -> new NotFoundException(GradebookErrors.GRADE_NOT_FOUND, "Grade not found"));
        GradeColumn column = structure.column(user.tenantId(), current.columnId())
                .orElseThrow(() -> new NotFoundException(GradebookErrors.GRADE_NOT_FOUND, "Grade not found"));
        access.require(Permission.GRADE_EDIT, AccessContext.course(column.courseId()));
        IfMatch.check(expectedVersion, current.version());
        validateScore(score, column.maxScore());
        Instant now = clock.instant();
        Grade next = current.withOverride(score, locked == null ? current.locked() : locked, user.userId(), now);
        return save(user, column, current, next, REASON_OVERRIDE, now);
    }

    private Cell save(CurrentUser user, GradeColumn column, Grade current, Grade next, String reason, Instant now) {
        if (!grades.update(next, current.version(), now)) {
            throw new ConflictException(IfMatch.VERSION_CONFLICT_CODE, "Grade was modified by someone else");
        }
        if (current.scoreDiffers(next)) {
            grades.insertHistory(user.tenantId(), current.id(), current.finalScore(), next.finalScore(), user.userId(), reason, now);
        }
        audit.record(AuditRecord.of(user.tenantId(), user.userId(), "grade.changed", "grade", current.id().toString())
                .withDiff(diff(current, next)));
        afterChange(user.tenantId(), column, next);
        return grades.find(user.tenantId(), current.id()).map(Cell::of).orElse(Cell.EMPTY);
    }

    private void afterChange(UUID tenantId, GradeColumn column, Grade grade) {
        if (column.sourceItemId() != null) {
            events.publishEvent(new GradeChanged(tenantId, column.courseId(), column.sourceItemId(), grade.userId(),
                    grade.finalScore(), column.maxScore(), grade.published()));
            return;
        }
        if (grade.published() && grade.finalScore() != null) {
            notifier.notifyStudent(tenantId, column, grade.userId(), grade.finalScore());
        }
    }

    private void requireStudent(UUID tenantId, UUID courseId, UUID userId) {
        boolean student = enrollment.membership(tenantId, courseId, userId)
                .map(member -> member.role() == CourseRole.STUDENT).orElse(false);
        if (!student) {
            throw ValidationException.single("userId", "not_student", "User is not a student of the course");
        }
    }

    static void validateScore(BigDecimal score, BigDecimal maxScore) {
        if (score == null) {
            return;
        }
        if (score.signum() < 0 || score.compareTo(maxScore) > 0 || score.stripTrailingZeros().scale() > SCORE_SCALE) {
            throw ValidationException.single(SCORE_FIELD, "out_of_range",
                    "Score must be between 0 and " + maxScore.toPlainString() + " with at most 2 decimals");
        }
    }

    private static Map<String, Object> diff(Grade before, Grade after) {
        Map<String, Object> diff = new HashMap<>();
        diff.put("oldScore", before.finalScore());
        diff.put("newScore", after.finalScore());
        diff.put("locked", after.locked());
        return diff;
    }
}
