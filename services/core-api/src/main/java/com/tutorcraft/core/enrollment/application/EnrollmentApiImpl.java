package com.tutorcraft.core.enrollment.application;

import com.tutorcraft.core.access.domain.CourseRole;
import com.tutorcraft.core.enrollment.EnrollmentApi;
import com.tutorcraft.core.enrollment.application.EnrollmentRepository.UpsertMode;
import com.tutorcraft.core.enrollment.application.EnrollmentRepository.UpsertResult;
import com.tutorcraft.core.enrollment.domain.Enrollment;
import com.tutorcraft.core.enrollment.domain.EnrollmentStatus;
import com.tutorcraft.core.shared.domain.Ids;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Реализация EnrollmentApi: только репозитории и ядро (правило против циклов бинов). */
@Component
class EnrollmentApiImpl implements EnrollmentApi {

    private final EnrollmentRepository enrollments;
    private final GroupRepository groups;
    private final EnrollmentChanges changes;
    private final Clock clock;

    EnrollmentApiImpl(EnrollmentRepository enrollments, GroupRepository groups, EnrollmentChanges changes, Clock clock) {
        this.enrollments = enrollments;
        this.groups = groups;
        this.changes = changes;
        this.clock = clock;
    }

    /** Пустой roles — любые роли. */
    @Override
    @Transactional(readOnly = true)
    public List<UUID> activeCourseIds(UUID tenantId, UUID userId, Set<CourseRole> roles) {
        return enrollments.activeCourseIds(tenantId, userId, roles, clock.instant());
    }

    /** Порядок — по дате записи (первый преподаватель — автор курса). */
    @Override
    @Transactional(readOnly = true)
    public List<Member> activeMembers(UUID tenantId, UUID courseId, Set<CourseRole> roles) {
        List<Enrollment> active = enrollments.activeInCourse(tenantId, courseId, roles, clock.instant());
        Map<UUID, List<UUID>> groupIds = groups.groupIdsOfUsers(tenantId, courseId,
                active.stream().map(Enrollment::userId).toList());
        return active.stream()
                .map(enrollment -> new Member(enrollment.userId(), enrollment.role(), enrollment.status().key(),
                        Set.copyOf(groupIds.getOrDefault(enrollment.userId(), List.of()))))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Map<CourseRole, Integer>> activeCountsByRole(UUID tenantId, Collection<UUID> courseIds) {
        Map<UUID, Map<CourseRole, Integer>> result = new HashMap<>();
        enrollments.countActiveByCourseAndRole(tenantId, courseIds, clock.instant())
                .forEach(row -> result.computeIfAbsent(row.courseId(), id -> new EnumMap<>(CourseRole.class))
                        .merge(row.role(), row.count(), Integer::sum));
        return result;
    }

    /** Запись в любом статусе (status в Member); доступ даёт только active в окне дат (см. CourseMembershipResolver). */
    @Override
    @Transactional(readOnly = true)
    public Optional<Member> membership(UUID tenantId, UUID courseId, UUID userId) {
        return enrollments.findByCourseAndUser(tenantId, courseId, userId)
                .map(enrollment -> new Member(userId, enrollment.role(), enrollment.status().key(),
                        groups.groupIdsOf(tenantId, courseId, userId)));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> groupIds(UUID tenantId, UUID courseId, UUID userId) {
        return groups.groupIdsOf(tenantId, courseId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> membersOfGroups(UUID tenantId, UUID courseId, Collection<UUID> groupIds) {
        return groupIds.isEmpty() ? Set.of() : groups.membersOf(tenantId, courseId, groupIds);
    }

    @Override
    @Transactional
    public void enrol(EnrolCommand command) {
        Instant now = clock.instant();
        Enrollment enrollment = new Enrollment(Ids.newId(), command.tenantId(), command.courseId(), command.userId(),
                command.role(), EnrollmentStatus.ACTIVE, command.method(), null, null, now, now, null);
        UpsertResult result = enrollments.upsert(enrollment, UpsertMode.REACTIVATE);
        changes.audit(command.tenantId(), command.actorId(), EnrollmentChanges.OBJECT_ENROLLMENT, result.id(),
                result.created() ? "created" : "reactivated", command.courseId(),
                Map.of("userId", command.userId().toString(), "role", result.roleKey(), "method", command.method()));
        changes.enrollmentChanged(command.tenantId(), command.courseId(), command.userId(), result.roleKey(),
                EnrollmentStatus.ACTIVE.key(), command.method(), result.created());
    }
}
