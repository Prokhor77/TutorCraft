package com.tutorcraft.core.gradebook.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.CourseRole;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.courses.CourseRef;
import com.tutorcraft.core.courses.CoursesApi;
import com.tutorcraft.core.enrollment.EnrollmentApi;
import com.tutorcraft.core.gradebook.application.GradebookViews.CourseGradeSummary;
import com.tutorcraft.core.gradebook.application.GradebookViews.MyCourseGrades;
import com.tutorcraft.core.gradebook.application.GradebookViews.MyGradesOverview;
import com.tutorcraft.core.gradebook.application.GradebookViews.MyItem;
import com.tutorcraft.core.gradebook.domain.Grade;
import com.tutorcraft.core.gradebook.domain.GradeColumn;
import com.tutorcraft.core.gradebook.spi.GradeFeedbackSource;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** «Мои оценки» студента (FR-GRADE-08): только опубликованные оценки и отзывы, итог по опубликованному. */
@Service
public class MyGradesService {

    private static final Set<CourseRole> LEARNER_ROLES = Set.of(CourseRole.STUDENT);

    private final CurrentUserProvider currentUser;
    private final AccessService access;
    private final EnrollmentApi enrollment;
    private final CoursesApi courses;
    private final CourseGradebooks gradebooks;
    private final GradeRepository grades;
    private final List<GradeFeedbackSource> feedbackSources;

    MyGradesService(CurrentUserProvider currentUser, AccessService access, EnrollmentApi enrollment, CoursesApi courses,
                    CourseGradebooks gradebooks, GradeRepository grades, List<GradeFeedbackSource> feedbackSources) {
        this.currentUser = currentUser;
        this.access = access;
        this.enrollment = enrollment;
        this.courses = courses;
        this.gradebooks = gradebooks;
        this.grades = grades;
        this.feedbackSources = List.copyOf(feedbackSources);
    }

    @Transactional(readOnly = true)
    public MyCourseGrades courseGrades(UUID courseId) {
        CurrentUser user = currentUser.require();
        access.require(Permission.GRADE_VIEW_OWN, AccessContext.course(courseId));
        CourseGradebook gradebook = gradebooks.load(user.tenantId(), courseId);
        Map<UUID, BigDecimal> published = publishedScores(user, gradebook);
        Map<UUID, Map<String, Object>> feedback = feedback(user, gradebook.columns());
        List<MyItem> items = gradebook.columns().stream()
                .map(column -> new MyItem(column.id(), column.name(), published.get(column.id()), column.maxScore(),
                        column.sourceItemId() == null ? null : feedback.get(column.sourceItemId())))
                .toList();
        BigDecimal percent = gradebook.finalPercent(published).orElse(null);
        return new MyCourseGrades(courseId, items, percent, gradebook.label(percent));
    }

    /** Сводка по всем курсам, где пользователь — студент. */
    @Transactional(readOnly = true)
    public MyGradesOverview overview() {
        CurrentUser user = currentUser.require();
        List<UUID> courseIds = enrollment.activeCourseIds(user.tenantId(), user.userId(), LEARNER_ROLES);
        Map<UUID, CourseRef> refs = courses.findCourses(user.tenantId(), courseIds);
        List<CourseGradeSummary> summaries = courseIds.stream()
                .filter(refs::containsKey)
                .map(courseId -> summary(user, refs.get(courseId)))
                .toList();
        return new MyGradesOverview(summaries);
    }

    private CourseGradeSummary summary(CurrentUser user, CourseRef course) {
        CourseGradebook gradebook = gradebooks.load(user.tenantId(), course.id());
        BigDecimal percent = gradebook.finalPercent(publishedScores(user, gradebook)).orElse(null);
        return new CourseGradeSummary(course.id(), course.title(), percent, gradebook.label(percent));
    }

    private Map<UUID, BigDecimal> publishedScores(CurrentUser user, CourseGradebook gradebook) {
        List<Grade> own = grades.ofUser(user.tenantId(), user.userId(), gradebook.columnIds());
        return PublishedScores.of(own);
    }

    private Map<UUID, Map<String, Object>> feedback(CurrentUser user, List<GradeColumn> columns) {
        List<UUID> sourceItemIds = columns.stream().map(GradeColumn::sourceItemId).filter(Objects::nonNull).toList();
        Map<UUID, Map<String, Object>> result = new HashMap<>();
        feedbackSources.forEach(source -> result.putAll(source.publishedFeedback(user.tenantId(), user.userId(), sourceItemIds)));
        return result;
    }
}
