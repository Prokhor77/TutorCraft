package com.tutorcraft.core.gradebook.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.CourseRole;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.courses.CourseRef;
import com.tutorcraft.core.courses.CoursesApi;
import com.tutorcraft.core.courses.ItemRef;
import com.tutorcraft.core.enrollment.EnrollmentApi;
import com.tutorcraft.core.gradebook.spi.GradingQueueSource;
import com.tutorcraft.core.gradebook.spi.GradingQueueSource.QueueEntry;
import com.tutorcraft.core.identity.UsersApi;
import com.tutorcraft.core.identity.UsersApi.UserRef;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.api.PageResponse;
import com.tutorcraft.core.shared.domain.ValidationException;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Единая очередь проверки (FR-GRADE-06, AC-8): все непроверенные работы со всех курсов, где пользователь может
 * проверять (submission.grade), отсортированные по сроку, затем по времени сдачи.
 */
@Service
public class GradingQueueService {

    private static final Set<CourseRole> GRADER_ROLES = Set.of(CourseRole.TEACHER, CourseRole.ASSISTANT);
    private static final Set<String> KINDS = Set.of(QueueEntry.SUBMISSION, QueueEntry.ESSAY);
    private static final String SEPARATE_GROUPS = "separate";

    private final CurrentUserProvider currentUser;
    private final AccessService access;
    private final EnrollmentApi enrollment;
    private final CoursesApi courses;
    private final UsersApi users;
    private final List<GradingQueueSource> sources;

    GradingQueueService(CurrentUserProvider currentUser, AccessService access, EnrollmentApi enrollment, CoursesApi courses,
                        UsersApi users, List<GradingQueueSource> sources) {
        this.currentUser = currentUser;
        this.access = access;
        this.enrollment = enrollment;
        this.courses = courses;
        this.users = users;
        this.sources = List.copyOf(sources);
    }

    @Transactional(readOnly = true)
    public PageResponse<GradebookViews.QueueEntry> queue(UUID courseId, String kind, String cursor, Integer limit) {
        CurrentUser user = currentUser.require();
        PageQuery page = PageQuery.of(null, limit);
        if (kind != null && !KINDS.contains(kind)) {
            throw ValidationException.single("type", "invalid", "Unknown queue entry type");
        }
        List<UUID> courseIds = gradableCourses(user, courseId);
        List<QueueEntry> pending = pendingEntries(user.tenantId(), user.userId(), courseIds, kind);
        Optional<QueueEntry> after = QueueCursor.decode(cursor);
        List<QueueEntry> remaining = pending.stream()
                .filter(entry -> after.isEmpty() || QueueCursor.ORDER.compare(entry, after.get()) > 0)
                .toList();
        List<QueueEntry> pageEntries = remaining.stream().limit(page.limit()).toList();
        String nextCursor = remaining.size() > page.limit() ? QueueCursor.encode(pageEntries.get(pageEntries.size() - 1)) : null;
        return new PageResponse<>(enrich(user.tenantId(), pageEntries), nextCursor);
    }

    /**
     * Размер очереди проверки произвольного пользователя (для счётчика реального времени, AC-8): те же курсы и
     * фильтры, что и у {@link #queue}, но права вычисляются для {@code userId}, а не для текущего пользователя.
     */
    @Transactional(readOnly = true)
    public int pendingCount(UUID tenantId, UUID userId) {
        List<UUID> courseIds = enrollment.activeCourseIds(tenantId, userId, GRADER_ROLES).stream()
                .filter(id -> access.permissionsOf(tenantId, userId, AccessContext.course(id)).contains(Permission.SUBMISSION_GRADE))
                .toList();
        return pendingEntries(tenantId, userId, courseIds, null).size();
    }

    private List<UUID> gradableCourses(CurrentUser user, UUID courseId) {
        if (courseId != null) {
            access.require(Permission.SUBMISSION_GRADE, AccessContext.course(courseId));
            return List.of(courseId);
        }
        return enrollment.activeCourseIds(user.tenantId(), user.userId(), GRADER_ROLES).stream()
                .filter(id -> access.can(Permission.SUBMISSION_GRADE, AccessContext.course(id)))
                .toList();
    }

    /** Записи источников по существующим (не удалённым) элементам, в порядке очереди. */
    private List<QueueEntry> pendingEntries(UUID tenantId, UUID userId, List<UUID> courseIds, String kind) {
        if (courseIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, Set<UUID>> restrictions = assistantRestrictions(tenantId, userId, courseIds);
        List<QueueEntry> entries = sources.stream()
                .flatMap(source -> source.pending(tenantId, courseIds).stream())
                .filter(entry -> kind == null || kind.equals(entry.kind()))
                .filter(entry -> !restrictions.containsKey(entry.courseId())
                        || restrictions.get(entry.courseId()).contains(entry.userId()))
                .toList();
        Set<UUID> liveItems = courses.findItems(tenantId, entries.stream().map(QueueEntry::itemId).distinct().toList())
                .keySet();
        return entries.stream().filter(entry -> liveItems.contains(entry.itemId())).sorted(QueueCursor.ORDER).toList();
    }

    /** Ассистент в курсе с режимом «separate» видит только работы участников своих групп. */
    private Map<UUID, Set<UUID>> assistantRestrictions(UUID tenantId, UUID userId, List<UUID> courseIds) {
        List<UUID> assisted = enrollment.activeCourseIds(tenantId, userId, Set.of(CourseRole.ASSISTANT));
        Map<UUID, CourseRef> refs = courses.findCourses(tenantId, assisted.stream().filter(courseIds::contains).toList());
        Map<UUID, Set<UUID>> restrictions = new HashMap<>();
        refs.values().stream().filter(course -> SEPARATE_GROUPS.equals(course.groupMode())).forEach(course -> {
            Set<UUID> groups = enrollment.groupIds(tenantId, course.id(), userId);
            restrictions.put(course.id(), groups.isEmpty() ? Set.of()
                    : enrollment.membersOfGroups(tenantId, course.id(), groups));
        });
        return restrictions;
    }

    private List<GradebookViews.QueueEntry> enrich(UUID tenantId, List<QueueEntry> entries) {
        Map<UUID, ItemRef> items = courses.findItems(tenantId, entries.stream().map(QueueEntry::itemId).distinct().toList());
        Map<UUID, CourseRef> courseRefs = courses.findCourses(tenantId, entries.stream().map(QueueEntry::courseId).distinct().toList());
        Map<UUID, UserRef> people = users.findAll(tenantId, entries.stream().map(QueueEntry::userId).distinct().toList());
        return entries.stream().map(entry -> new GradebookViews.QueueEntry(entry.kind(), entry.id(), entry.courseId(),
                        courseRefs.containsKey(entry.courseId()) ? courseRefs.get(entry.courseId()).title() : null,
                        entry.itemId(), items.containsKey(entry.itemId()) ? items.get(entry.itemId()).title() : entry.itemTitle(),
                        entry.userId(), people.containsKey(entry.userId()) ? people.get(entry.userId()).displayName() : null,
                        entry.submittedAt(), entry.dueAt(), entry.late()))
                .toList();
    }
}
