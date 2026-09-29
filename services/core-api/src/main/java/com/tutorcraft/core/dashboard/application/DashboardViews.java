package com.tutorcraft.core.dashboard.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Представления главных страниц (контракт §2). */
public final class DashboardViews {

    private DashboardViews() {
    }

    /** status: статус активности; null — для главной преподавателя. */
    public record TaskEntry(UUID itemId, UUID courseId, String courseTitle, String itemTitle, String itemType, Instant dueAt,
                            String status) {
    }

    public record RecentlyGraded(UUID itemId, UUID courseId, String itemTitle, String courseTitle, BigDecimal score,
                                 BigDecimal maxScore, Instant gradedAt) {
    }

    public record ContinueLearning(UUID courseId, String courseTitle, UUID itemId, String itemTitle, int progressPercent) {
    }

    public record MyTasks(List<TaskEntry> overdue, List<TaskEntry> today, List<TaskEntry> thisWeek, List<TaskEntry> later,
                          List<RecentlyGraded> recentlyGraded, List<ContinueLearning> continueLearning) {
    }

    public record ToGrade(UUID courseId, String courseTitle, int count) {
    }

    public record RecentPost(UUID discussionId, UUID courseId, String title, String authorName, Instant createdAt) {
    }

    public record TeacherHome(List<ToGrade> toGrade, int toGradeTotal, List<TaskEntry> upcomingDeadlines,
                              List<RecentPost> recentPosts) {
    }

    public record CourseCard(UUID id, String title, String shortName, String coverUrl, UUID categoryId, String role,
                             Integer progressPercent, String visibility) {
    }
}
