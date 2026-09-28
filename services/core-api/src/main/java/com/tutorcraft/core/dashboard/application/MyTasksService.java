package com.tutorcraft.core.dashboard.application;

import com.tutorcraft.core.access.domain.CourseRole;
import com.tutorcraft.core.courses.CourseRef;
import com.tutorcraft.core.courses.CoursesApi;
import com.tutorcraft.core.courses.ItemRef;
import com.tutorcraft.core.courses.ItemType;
import com.tutorcraft.core.dashboard.application.DashboardViews.ContinueLearning;
import com.tutorcraft.core.dashboard.application.DashboardViews.MyTasks;
import com.tutorcraft.core.dashboard.application.DashboardViews.RecentlyGraded;
import com.tutorcraft.core.dashboard.application.DashboardViews.TaskEntry;
import com.tutorcraft.core.dashboard.domain.DeadlineGrouper;
import com.tutorcraft.core.dashboard.domain.DeadlineGrouper.Bucket;
import com.tutorcraft.core.enrollment.EnrollmentApi;
import com.tutorcraft.core.gradebook.GradebookApi;
import com.tutorcraft.core.identity.UsersApi;
import com.tutorcraft.core.progress.ProgressApi;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Главная студента «Мои задачи» (FR-DASH-01, AC-9). */
@Service
public class MyTasksService {

    private static final Set<CourseRole> LEARNER = Set.of(CourseRole.STUDENT);
    private static final Set<ItemType> TASK_TYPES = Set.of(ItemType.ASSIGNMENT, ItemType.QUIZ, ItemType.FORUM);
    private static final int RECENTLY_GRADED_LIMIT = 5;

    private final CurrentUserProvider currentUser;
    private final EnrollmentApi enrollment;
    private final CoursesApi courses;
    private final UsersApi users;
    private final GradebookApi gradebook;
    private final ObjectProvider<ProgressApi> progress;
    private final ActivityStatuses statuses;
    private final Clock clock;

    MyTasksService(CurrentUserProvider currentUser, EnrollmentApi enrollment, CoursesApi courses, UsersApi users,
                   GradebookApi gradebook, ObjectProvider<ProgressApi> progress, ActivityStatuses statuses, Clock clock) {
        this.currentUser = currentUser;
        this.enrollment = enrollment;
        this.courses = courses;
        this.users = users;
        this.gradebook = gradebook;
        this.progress = progress;
        this.statuses = statuses;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MyTasks tasks() {
        CurrentUser user = currentUser.require();
        Instant now = clock.instant();
        List<UUID> courseIds = enrollment.activeCourseIds(user.tenantId(), user.userId(), LEARNER);
        Map<UUID, CourseRef> refs = courses.findCourses(user.tenantId(), courseIds);
        Map<Bucket, List<TaskEntry>> groups = DeadlineGrouper.group(openTasks(user, refs, now), TaskEntry::dueAt, now,
                zoneOf(user));
        return new MyTasks(groups.get(Bucket.OVERDUE), groups.get(Bucket.TODAY), groups.get(Bucket.THIS_WEEK),
                groups.get(Bucket.LATER), recentlyGraded(user), continueLearning(user, refs));
    }

    /** Несданные видимые активности со сроком; закрытые безвозвратно (closeAt прошёл) не показываются. */
    private List<TaskEntry> openTasks(CurrentUser user, Map<UUID, CourseRef> refs, Instant now) {
        if (refs.isEmpty()) {
            return List.of();
        }
        List<ItemRef> items = courses.itemsOfCourses(user.tenantId(), refs.keySet(), TASK_TYPES).stream()
                .filter(item -> item.dueAt() != null)
                .filter(item -> item.closeAt() == null || !item.closeAt().isBefore(now))
                .filter(item -> courses.isVisibleToLearners(user.tenantId(), item))
                .toList();
        Map<UUID, String> itemStatuses = statuses.of(user.tenantId(), user.userId(), items);
        return items.stream()
                .filter(item -> !ActivityStatuses.done(itemStatuses.get(item.id())))
                .map(item -> new TaskEntry(item.id(), item.courseId(), refs.get(item.courseId()).title(), item.title(),
                        item.type().key(), item.dueAt(), itemStatuses.get(item.id())))
                .toList();
    }

    private List<RecentlyGraded> recentlyGraded(CurrentUser user) {
        List<GradebookApi.PublishedGrade> grades = gradebook.recentlyPublished(user.tenantId(), user.userId(),
                RECENTLY_GRADED_LIMIT);
        Map<UUID, CourseRef> refs = courses.findCourses(user.tenantId(),
                grades.stream().map(GradebookApi.PublishedGrade::courseId).distinct().toList());
        return grades.stream()
                .map(grade -> new RecentlyGraded(grade.sourceItemId(), grade.courseId(), grade.itemName(),
                        refs.containsKey(grade.courseId()) ? refs.get(grade.courseId()).title() : null, grade.score(),
                        grade.maxScore(), grade.publishedAt()))
                .toList();
    }

    /** «Продолжить обучение»: последний открытый элемент в каждом курсе (если модуль progress доступен). */
    private List<ContinueLearning> continueLearning(CurrentUser user, Map<UUID, CourseRef> refs) {
        ProgressApi progressApi = progress.getIfAvailable();
        if (progressApi == null || refs.isEmpty()) {
            return List.of();
        }
        Map<UUID, Integer> percents = progressApi.completionPercents(user.tenantId(), user.userId(), refs.keySet());
        Map<UUID, UUID> lastItems = refs.keySet().stream()
                .map(courseId -> progressApi.lastViewedItem(user.tenantId(), courseId, user.userId())
                        .map(itemId -> Map.entry(courseId, itemId)))
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        Map<UUID, ItemRef> items = courses.findItems(user.tenantId(), lastItems.values());
        return lastItems.entrySet().stream()
                .filter(entry -> items.containsKey(entry.getValue()))
                .map(entry -> new ContinueLearning(entry.getKey(), refs.get(entry.getKey()).title(), entry.getValue(),
                        items.get(entry.getValue()).title(), Objects.requireNonNullElse(percents.get(entry.getKey()), 0)))
                .toList();
    }

    private ZoneId zoneOf(CurrentUser user) {
        return users.find(user.tenantId(), user.userId())
                .map(UsersApi.UserRef::timezone)
                .flatMap(MyTasksService::parseZone)
                .orElse(ZoneOffset.UTC);
    }

    private static Optional<ZoneId> parseZone(String zone) {
        try {
            return Optional.of(ZoneId.of(zone));
        } catch (DateTimeException e) {
            return Optional.empty();
        }
    }
}
