package com.tutorcraft.core.dashboard.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.CourseRole;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.courses.CourseRef;
import com.tutorcraft.core.courses.CoursesApi;
import com.tutorcraft.core.courses.ItemRef;
import com.tutorcraft.core.courses.ItemType;
import com.tutorcraft.core.dashboard.application.DashboardViews.RecentPost;
import com.tutorcraft.core.dashboard.application.DashboardViews.TaskEntry;
import com.tutorcraft.core.dashboard.application.DashboardViews.TeacherHome;
import com.tutorcraft.core.dashboard.application.DashboardViews.ToGrade;
import com.tutorcraft.core.dashboard.spi.RecentPostsSource;
import com.tutorcraft.core.enrollment.EnrollmentApi;
import com.tutorcraft.core.gradebook.spi.GradingQueueSource;
import com.tutorcraft.core.gradebook.spi.GradingQueueSource.QueueEntry;
import com.tutorcraft.core.identity.UsersApi;
import com.tutorcraft.core.identity.UsersApi.UserRef;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Главная преподавателя (FR-DASH-02): «на проверку» по курсам, ближайшие дедлайны, последние посты форумов. */
@Service
public class TeacherHomeService {

    private static final Set<CourseRole> TEACHING = Set.of(CourseRole.TEACHER, CourseRole.ASSISTANT);
    private static final Set<ItemType> DEADLINE_TYPES = Set.of(ItemType.ASSIGNMENT, ItemType.QUIZ, ItemType.FORUM);
    private static final Duration UPCOMING_HORIZON = Duration.ofDays(14);
    private static final int UPCOMING_LIMIT = 10;
    private static final int RECENT_POSTS_LIMIT = 10;

    private final CurrentUserProvider currentUser;
    private final AccessService access;
    private final EnrollmentApi enrollment;
    private final CoursesApi courses;
    private final UsersApi users;
    private final List<GradingQueueSource> queueSources;
    private final ObjectProvider<RecentPostsSource> posts;
    private final Clock clock;

    TeacherHomeService(CurrentUserProvider currentUser, AccessService access, EnrollmentApi enrollment, CoursesApi courses,
                       UsersApi users, List<GradingQueueSource> queueSources, ObjectProvider<RecentPostsSource> posts,
                       Clock clock) {
        this.currentUser = currentUser;
        this.access = access;
        this.enrollment = enrollment;
        this.courses = courses;
        this.users = users;
        this.queueSources = List.copyOf(queueSources);
        this.posts = posts;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TeacherHome home() {
        CurrentUser user = currentUser.require();
        List<UUID> courseIds = enrollment.activeCourseIds(user.tenantId(), user.userId(), TEACHING);
        Map<UUID, CourseRef> refs = courses.findCourses(user.tenantId(), courseIds);
        List<ToGrade> toGrade = toGrade(user, refs);
        return new TeacherHome(toGrade, toGrade.stream().mapToInt(ToGrade::count).sum(), upcoming(user, refs),
                recentPosts(user, refs));
    }

    private List<ToGrade> toGrade(CurrentUser user, Map<UUID, CourseRef> refs) {
        List<UUID> gradable = refs.keySet().stream()
                .filter(courseId -> access.can(Permission.SUBMISSION_GRADE, AccessContext.course(courseId)))
                .toList();
        if (gradable.isEmpty()) {
            return List.of();
        }
        List<QueueEntry> pending = queueSources.stream().flatMap(source -> source.pending(user.tenantId(), gradable).stream()).toList();
        Set<UUID> liveItems = courses.findItems(user.tenantId(), pending.stream().map(QueueEntry::itemId).distinct().toList()).keySet();
        Map<UUID, Long> counts = pending.stream().filter(entry -> liveItems.contains(entry.itemId()))
                .collect(Collectors.groupingBy(QueueEntry::courseId, Collectors.counting()));
        return counts.entrySet().stream()
                .map(entry -> new ToGrade(entry.getKey(), refs.get(entry.getKey()).title(), entry.getValue().intValue()))
                .sorted(Comparator.comparingInt(ToGrade::count).reversed().thenComparing(ToGrade::courseTitle))
                .toList();
    }

    private List<TaskEntry> upcoming(CurrentUser user, Map<UUID, CourseRef> refs) {
        if (refs.isEmpty()) {
            return List.of();
        }
        Instant now = clock.instant();
        Instant horizon = now.plus(UPCOMING_HORIZON);
        return courses.itemsOfCourses(user.tenantId(), refs.keySet(), DEADLINE_TYPES).stream()
                .filter(item -> item.dueAt() != null && !item.dueAt().isBefore(now) && item.dueAt().isBefore(horizon))
                .sorted(Comparator.comparing(ItemRef::dueAt))
                .limit(UPCOMING_LIMIT)
                .map(item -> new TaskEntry(item.id(), item.courseId(), refs.get(item.courseId()).title(), item.title(),
                        item.type().key(), item.dueAt(), null))
                .toList();
    }

    private List<RecentPost> recentPosts(CurrentUser user, Map<UUID, CourseRef> refs) {
        RecentPostsSource source = posts.getIfAvailable();
        if (source == null || refs.isEmpty()) {
            return List.of();
        }
        List<RecentPostsSource.RecentPost> recent = source.recent(user.tenantId(), refs.keySet(), RECENT_POSTS_LIMIT);
        Map<UUID, UserRef> authors = users.findAll(user.tenantId(),
                recent.stream().map(RecentPostsSource.RecentPost::authorId).distinct().toList());
        return recent.stream()
                .map(post -> new RecentPost(post.discussionId(), post.courseId(), post.title(),
                        authors.containsKey(post.authorId()) ? authors.get(post.authorId()).displayName() : null,
                        post.createdAt()))
                .toList();
    }
}
