package com.tutorcraft.core.progress.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.CourseRole;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.courses.CoursesApi;
import com.tutorcraft.core.courses.ItemRef;
import com.tutorcraft.core.enrollment.EnrollmentApi;
import com.tutorcraft.core.enrollment.EnrollmentApi.Member;
import com.tutorcraft.core.identity.UsersApi;
import com.tutorcraft.core.identity.UsersApi.UserRef;
import com.tutorcraft.core.progress.domain.ProgressMath;
import com.tutorcraft.core.shared.i18n.Messages;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Выполнение курса: своё (GET /courses/{id}/completion/me) и отчёт по участникам (FR-REPORT, completion.viewAll). */
@Service
public class CompletionQueryService {

    private final CompletionRepository completions;
    private final CoursesApi courses;
    private final EnrollmentApi enrollment;
    private final UsersApi users;
    private final AccessService access;
    private final CurrentUserProvider currentUser;
    private final Messages messages;

    public CompletionQueryService(CompletionRepository completions, CoursesApi courses, EnrollmentApi enrollment,
                                  UsersApi users, AccessService access, CurrentUserProvider currentUser, Messages messages) {
        this.completions = completions;
        this.courses = courses;
        this.enrollment = enrollment;
        this.users = users;
        this.access = access;
        this.currentUser = currentUser;
        this.messages = messages;
    }

    public MyCompletion mine(UUID courseId) {
        CurrentUser user = currentUser.require();
        access.require(Permission.COURSE_VIEW, AccessContext.course(courseId));
        List<ItemRef> tracked = TrackedItems.of(courses.itemsOfCourse(user.tenantId(), courseId));
        Set<UUID> completed = completions.completedItems(user.tenantId(), courseId, user.userId());
        Map<UUID, String> items = new LinkedHashMap<>();
        tracked.forEach(item -> items.put(item.id(), completed.contains(item.id())
                ? LearnerStateAdapter.COMPLETE : LearnerStateAdapter.INCOMPLETE));
        Integer percent = ProgressMath.percent(tracked.stream().map(ItemRef::id).toList(), completed);
        return new MyCompletion(percent == null ? 0 : percent,
                completions.courseCompletedAt(user.tenantId(), courseId, user.userId()).orElse(null), items);
    }

    public ProgressReport report(UUID courseId, UUID groupId) {
        CurrentUser user = currentUser.require();
        access.require(Permission.COMPLETION_VIEW_ALL, AccessContext.course(courseId));
        List<ItemRef> tracked = TrackedItems.of(courses.itemsOfCourse(user.tenantId(), courseId));
        List<UUID> trackedIds = tracked.stream().map(ItemRef::id).toList();
        Set<UUID> learners = learners(user.tenantId(), courseId, groupId);
        Map<UUID, UserRef> names = users.findAll(user.tenantId(), learners);
        Map<UUID, Set<UUID>> completedByUser = completions.completedItemsByUser(user.tenantId(), courseId);
        Map<UUID, Instant> completedAt = completions.courseCompletions(user.tenantId(), courseId);
        List<ReportRow> rows = learners.stream().map(learner -> {
            Set<UUID> done = completedByUser.getOrDefault(learner, Set.of());
            Integer percent = ProgressMath.percent(trackedIds, done);
            return new ReportRow(learner, displayName(names.get(learner)), trackedIds.stream().filter(done::contains).toList(),
                    percent == null ? 0 : percent, completedAt.get(learner));
        }).sorted(Comparator.comparing(ReportRow::userName, String.CASE_INSENSITIVE_ORDER)).toList();
        return new ProgressReport(tracked.stream().map(item -> new ReportItem(item.id(), item.title())).toList(), rows);
    }

    /** Отчёт в CSV с локализованными заголовками. */
    public String reportCsv(UUID courseId, UUID groupId) {
        List<String> headers = List.of(messages.get("progress.csv.user"), messages.get("progress.csv.percent"),
                messages.get("progress.csv.completed_at"));
        return ProgressReportCsv.write(report(courseId, groupId), headers);
    }

    private Set<UUID> learners(UUID tenantId, UUID courseId, UUID groupId) {
        Set<UUID> students = enrollment.activeMembers(tenantId, courseId, Set.of(CourseRole.STUDENT)).stream()
                .map(Member::userId).collect(Collectors.toSet());
        if (groupId == null) {
            return students;
        }
        Set<UUID> inGroup = enrollment.membersOfGroups(tenantId, courseId, Set.of(groupId));
        return students.stream().filter(inGroup::contains).collect(Collectors.toSet());
    }

    private static String displayName(UserRef user) {
        return user == null ? "" : user.displayName();
    }

    public record MyCompletion(int percent, Instant completedAt, Map<UUID, String> items) {
    }

    public record ReportItem(UUID id, String title) {
    }

    public record ReportRow(UUID userId, String userName, List<UUID> completed, int percent, Instant completedAt) {
    }

    public record ProgressReport(List<ReportItem> items, List<ReportRow> rows) {
    }
}
