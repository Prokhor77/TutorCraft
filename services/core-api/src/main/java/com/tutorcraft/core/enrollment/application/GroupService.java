package com.tutorcraft.core.enrollment.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.CourseRole;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.enrollment.domain.CourseGroup;
import com.tutorcraft.core.enrollment.domain.Enrollment;
import com.tutorcraft.core.enrollment.domain.GroupDistribution;
import com.tutorcraft.core.enrollment.domain.GroupDistribution.Strategy;
import com.tutorcraft.core.enrollment.domain.GroupNames;
import com.tutorcraft.core.shared.domain.Ids;
import com.tutorcraft.core.shared.domain.ValidationException;
import com.tutorcraft.core.shared.i18n.Messages;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Группы курса: CRUD, ручное и автоматическое распределение (FR-ENROL-05). */
@Service
public class GroupService {

    public static final int MAX_MEMBERS = 5000;
    private static final int MAX_PREFIX = 80;
    private static final String FIELD_USER_IDS = "userIds";

    private final GroupRepository groups;
    private final EnrollmentRepository enrollments;
    private final AccessService access;
    private final CurrentUserProvider currentUser;
    private final EnrollmentChanges changes;
    private final Messages messages;
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public GroupService(GroupRepository groups, EnrollmentRepository enrollments, AccessService access,
                        CurrentUserProvider currentUser, EnrollmentChanges changes, Messages messages, Clock clock) {
        this.groups = groups;
        this.enrollments = enrollments;
        this.access = access;
        this.currentUser = currentUser;
        this.changes = changes;
        this.messages = messages;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<GroupView> list(UUID courseId) {
        CurrentUser user = currentUser.require();
        access.require(Permission.ENROLLMENT_VIEW, AccessContext.course(courseId));
        return groups.listByCourse(user.tenantId(), courseId).stream().map(GroupService::toView).toList();
    }

    @Transactional
    public GroupView create(UUID courseId, String name) {
        CurrentUser actor = currentUser.require();
        access.require(Permission.GROUP_MANAGE, AccessContext.course(courseId));
        CourseGroup group = new CourseGroup(Ids.newId(), actor.tenantId(), courseId, CourseGroup.validName(name), List.of());
        groups.insert(group, clock.instant());
        changes.audit(actor.tenantId(), actor.userId(), EnrollmentChanges.OBJECT_GROUP, group.id(), "created", courseId, null);
        return toView(group);
    }

    @Transactional
    public GroupView rename(UUID groupId, String name) {
        CurrentUser actor = currentUser.require();
        CourseGroup group = requireManaged(actor, groupId);
        String validName = CourseGroup.validName(name);
        groups.rename(actor.tenantId(), groupId, validName, clock.instant());
        changes.audit(actor.tenantId(), actor.userId(), EnrollmentChanges.OBJECT_GROUP, groupId, "updated", group.courseId(), null);
        return new GroupView(groupId, validName, group.memberIds());
    }

    @Transactional
    public void delete(UUID groupId) {
        CurrentUser actor = currentUser.require();
        CourseGroup group = requireManaged(actor, groupId);
        groups.delete(actor.tenantId(), groupId);
        changes.audit(actor.tenantId(), actor.userId(), EnrollmentChanges.OBJECT_GROUP, groupId, "deleted", group.courseId(), null);
    }

    /** Заменяет состав группы; участники должны быть записаны на курс. */
    @Transactional
    public GroupView replaceMembers(UUID groupId, List<UUID> userIds) {
        CurrentUser actor = currentUser.require();
        CourseGroup group = requireManaged(actor, groupId);
        Set<UUID> members = new LinkedHashSet<>(userIds == null ? List.of() : userIds);
        if (members.size() > MAX_MEMBERS) {
            throw ValidationException.single(FIELD_USER_IDS, "too_many", "Too many members");
        }
        if (!enrollments.enrolledUserIds(actor.tenantId(), group.courseId(), members).containsAll(members)) {
            throw ValidationException.single(FIELD_USER_IDS, "not_enrolled", "All members must be enrolled in the course");
        }
        groups.replaceMembers(actor.tenantId(), groupId, members, clock.instant());
        changes.audit(actor.tenantId(), actor.userId(), EnrollmentChanges.OBJECT_GROUP, groupId, "members_changed",
                group.courseId(), Map.of("count", members.size()));
        return new GroupView(groupId, group.name(), List.copyOf(members));
    }

    /** Случайно распределяет активных студентов по новым группам «Префикс N». */
    @Transactional
    public List<GroupView> autoCreate(UUID courseId, String strategy, int value, String prefix) {
        CurrentUser actor = currentUser.require();
        access.require(Permission.GROUP_MANAGE, AccessContext.course(courseId));
        Strategy parsed = Strategy.fromKey(strategy);
        List<UUID> students = enrollments.activeInCourse(actor.tenantId(), courseId, EnumSet.of(CourseRole.STUDENT),
                clock.instant()).stream().map(Enrollment::userId).toList();
        List<List<UUID>> distribution = GroupDistribution.distribute(students, parsed, value, random);
        Set<String> existing = groups.listByCourse(actor.tenantId(), courseId).stream()
                .map(CourseGroup::name).collect(Collectors.toSet());
        List<String> names = GroupNames.next(prefix(prefix), existing, distribution.size());
        List<GroupView> created = new ArrayList<>();
        for (int i = 0; i < distribution.size(); i++) {
            CourseGroup group = new CourseGroup(Ids.newId(), actor.tenantId(), courseId, names.get(i), distribution.get(i));
            Instant now = clock.instant();
            groups.insert(group, now);
            groups.replaceMembers(actor.tenantId(), group.id(), group.memberIds(), now);
            created.add(toView(group));
        }
        changes.audit(actor.tenantId(), actor.userId(), EnrollmentChanges.OBJECT_GROUP, courseId, "auto_created", courseId,
                Map.of("strategy", parsed.key(), "value", value, "groups", created.size()));
        return created;
    }

    private CourseGroup requireManaged(CurrentUser actor, UUID groupId) {
        CourseGroup group = groups.find(actor.tenantId(), groupId).orElseThrow(EnrollmentErrors::groupNotFound);
        access.require(Permission.GROUP_MANAGE, AccessContext.course(group.courseId()));
        return group;
    }

    private String prefix(String requested) {
        String value = requested == null || requested.isBlank() ? messages.get(EnrollmentErrors.AUTO_GROUP_PREFIX) : requested.trim();
        if (value.length() > MAX_PREFIX) {
            throw ValidationException.single("prefix", "too_long", "Maximum length is " + MAX_PREFIX);
        }
        return value;
    }

    private static GroupView toView(CourseGroup group) {
        return new GroupView(group.id(), group.name(), group.memberIds());
    }
}
