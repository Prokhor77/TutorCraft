package com.tutorcraft.core.enrollment.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.CourseRole;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.enrollment.EnrollmentApi.EnrolCommand;
import com.tutorcraft.core.enrollment.application.EnrollmentRepository.EnrollmentFilter;
import com.tutorcraft.core.enrollment.application.EnrollmentRepository.UpsertMode;
import com.tutorcraft.core.enrollment.application.EnrollmentRepository.UpsertResult;
import com.tutorcraft.core.enrollment.domain.Enrollment;
import com.tutorcraft.core.enrollment.domain.EnrollmentStatus;
import com.tutorcraft.core.identity.UsersApi;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.api.PageResponse;
import com.tutorcraft.core.shared.domain.BusinessRuleException;
import com.tutorcraft.core.shared.domain.Ids;
import com.tutorcraft.core.shared.domain.ValidationException;
import com.tutorcraft.core.shared.domain.NotFoundException;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Участники курса: список, ручная массовая запись, изменение и удаление записи (FR-ENROL-01/04). */
@Service
public class EnrollmentService {

    public static final int MAX_BULK_USERS = 500;
    private static final int MAX_QUERY_LENGTH = 200;
    private static final String FIELD_USER_IDS = "userIds";

    private final EnrollmentRepository enrollments;
    private final GroupRepository groups;
    private final UsersApi users;
    private final AccessService access;
    private final CurrentUserProvider currentUser;
    private final EnrollmentChanges changes;
    private final EnrollmentViews views;
    private final Clock clock;

    public EnrollmentService(EnrollmentRepository enrollments, GroupRepository groups, UsersApi users, AccessService access,
                             CurrentUserProvider currentUser, EnrollmentChanges changes, EnrollmentViews views, Clock clock) {
        this.enrollments = enrollments;
        this.groups = groups;
        this.users = users;
        this.access = access;
        this.currentUser = currentUser;
        this.changes = changes;
        this.views = views;
        this.clock = clock;
    }

    @Transactional(noRollbackFor = NotFoundException.class)
    public PageResponse<EnrollmentView> list(UUID courseId, String q, String role, UUID groupId, PageQuery page) {
        CurrentUser user = currentUser.require();
        access.require(Permission.ENROLLMENT_VIEW, AccessContext.course(courseId));
        String roleKey = role == null || role.isBlank() ? null : CourseRole.fromKey(role).key();
        EnrollmentFilter filter = new EnrollmentFilter(normalizeQuery(q), roleKey, groupId);
        List<Enrollment> rows = enrollments.list(user.tenantId(), courseId, filter, page);
        PageResponse<Enrollment> result = page.toPage(rows, Enrollment::createdAt, Enrollment::id);
        return new PageResponse<>(views.views(user.tenantId(), courseId, result.items()), result.nextCursor());
    }

    /** Массовая запись (повторная — обновляет роль/даты и реактивирует). @return число новых записей */
    @Transactional
    public int enrol(UUID courseId, List<UUID> userIds, String role, Instant startsAt, Instant endsAt) {
        CurrentUser actor = currentUser.require();
        access.require(Permission.ENROLLMENT_MANAGE, AccessContext.course(courseId));
        CourseRole courseRole = CourseRole.fromKey(role);
        Enrollment.validateWindow(startsAt, endsAt);
        Set<UUID> distinct = requireUsers(actor.tenantId(), userIds);
        Instant now = clock.instant();
        int created = 0;
        for (UUID userId : distinct) {
            Enrollment enrollment = new Enrollment(Ids.newId(), actor.tenantId(), courseId, userId, courseRole,
                    EnrollmentStatus.ACTIVE, EnrolCommand.METHOD_MANUAL, startsAt, endsAt, now, now, null);
            UpsertResult result = enrollments.upsert(enrollment, UpsertMode.OVERRIDE);
            created += result.created() ? 1 : 0;
            record(actor, courseId, userId, result, courseRole);
        }
        return created;
    }

    @Transactional
    public EnrollmentView update(UUID enrollmentId, EnrollmentPatch patch) {
        CurrentUser actor = currentUser.require();
        Enrollment current = enrollments.find(actor.tenantId(), enrollmentId).orElseThrow(EnrollmentErrors::enrollmentNotFound);
        access.require(Permission.ENROLLMENT_MANAGE, AccessContext.course(current.courseId()));
        CourseRole role = patch.role() == null ? current.role() : CourseRole.fromKey(patch.role());
        EnrollmentStatus status = patch.status() == null ? current.status() : EnrollmentStatus.fromKey(patch.status());
        Instant startsAt = patch.startsAtSet() ? patch.startsAt() : current.startsAt();
        Instant endsAt = patch.endsAtSet() ? patch.endsAt() : current.endsAt();
        Enrollment.validateWindow(startsAt, endsAt);
        Enrollment updated = current.withChanges(role, status, startsAt, endsAt);
        if (!updated.activeAt(clock.instant()) || role != CourseRole.TEACHER) {
            requireAnotherTeacher(current);
        }
        enrollments.update(updated, clock.instant());
        changes.audit(actor.tenantId(), actor.userId(), EnrollmentChanges.OBJECT_ENROLLMENT, enrollmentId, "updated",
                current.courseId(), diff(current, updated));
        changes.enrollmentChanged(actor.tenantId(), current.courseId(), current.userId(), role.key(), status.key(),
                current.method(), false);
        return views.view(updated);
    }

    /** Удаляет запись и членство в группах курса; сдачи и оценки остаются (FR-ENROL-04). */
    @Transactional
    public void delete(UUID enrollmentId) {
        CurrentUser actor = currentUser.require();
        Enrollment current = enrollments.find(actor.tenantId(), enrollmentId).orElseThrow(EnrollmentErrors::enrollmentNotFound);
        access.require(Permission.ENROLLMENT_MANAGE, AccessContext.course(current.courseId()));
        requireAnotherTeacher(current);
        groups.removeUserFromCourseGroups(actor.tenantId(), current.courseId(), current.userId());
        enrollments.delete(actor.tenantId(), enrollmentId);
        changes.audit(actor.tenantId(), actor.userId(), EnrollmentChanges.OBJECT_ENROLLMENT, enrollmentId, "deleted",
                current.courseId(), Map.of("userId", current.userId().toString(), "role", current.role().key()));
        changes.enrollmentChanged(actor.tenantId(), current.courseId(), current.userId(), current.role().key(),
                EnrollmentChanges.STATUS_DELETED, current.method(), false);
    }

    /** Курс не должен остаться без активного преподавателя. */
    private void requireAnotherTeacher(Enrollment enrollment) {
        boolean activeTeacher = enrollment.role() == CourseRole.TEACHER && enrollment.activeAt(clock.instant());
        if (activeTeacher && enrollments.countActiveByRole(enrollment.tenantId(), enrollment.courseId(), CourseRole.TEACHER) <= 1) {
            throw new BusinessRuleException(EnrollmentErrors.LAST_TEACHER, "The course must keep at least one teacher");
        }
    }

    private Set<UUID> requireUsers(UUID tenantId, List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty() || userIds.size() > MAX_BULK_USERS) {
            throw ValidationException.single(FIELD_USER_IDS, "size", "Between 1 and " + MAX_BULK_USERS + " users are required");
        }
        Set<UUID> distinct = new LinkedHashSet<>(userIds);
        if (!users.findAll(tenantId, distinct).keySet().containsAll(distinct)) {
            throw ValidationException.single(FIELD_USER_IDS, "not_found", "Some users do not exist");
        }
        return distinct;
    }

    private void record(CurrentUser actor, UUID courseId, UUID userId, UpsertResult result, CourseRole role) {
        changes.audit(actor.tenantId(), actor.userId(), EnrollmentChanges.OBJECT_ENROLLMENT, result.id(),
                result.created() ? "created" : "updated", courseId,
                Map.of("userId", userId.toString(), "role", role.key(), "method", EnrolCommand.METHOD_MANUAL));
        changes.enrollmentChanged(actor.tenantId(), courseId, userId, role.key(), EnrollmentStatus.ACTIVE.key(),
                EnrolCommand.METHOD_MANUAL, result.created());
    }

    private static Map<String, Object> diff(Enrollment before, Enrollment after) {
        Map<String, Object> diff = new LinkedHashMap<>();
        if (before.role() != after.role()) {
            diff.put("role", after.role().key());
        }
        if (before.status() != after.status()) {
            diff.put("status", after.status().key());
        }
        diff.put("startsAt", after.startsAt() == null ? null : after.startsAt().toString());
        diff.put("endsAt", after.endsAt() == null ? null : after.endsAt().toString());
        return diff;
    }

    private static String normalizeQuery(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String trimmed = q.trim();
        return trimmed.length() > MAX_QUERY_LENGTH ? trimmed.substring(0, MAX_QUERY_LENGTH) : trimmed;
    }

    /** PATCH /enrollments/{id}: null role/status — не менять; *Set — поле передано (null очищает дату). */
    public record EnrollmentPatch(String role, String status, boolean startsAtSet, Instant startsAt, boolean endsAtSet,
                                  Instant endsAt) {
    }
}
