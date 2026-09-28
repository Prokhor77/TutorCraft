package com.tutorcraft.core.gradebook.application;

import com.tutorcraft.core.access.domain.CourseRole;
import com.tutorcraft.core.enrollment.EnrollmentApi;
import com.tutorcraft.core.gradebook.application.GradebookViews.Cell;
import com.tutorcraft.core.gradebook.application.GradebookViews.Column;
import com.tutorcraft.core.gradebook.application.GradebookViews.Gradebook;
import com.tutorcraft.core.gradebook.application.GradebookViews.Row;
import com.tutorcraft.core.gradebook.domain.Grade;
import com.tutorcraft.core.identity.UsersApi;
import com.tutorcraft.core.identity.UsersApi.UserRef;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Сборка таблицы журнала (строки — активные студенты, итог по всем оценкам). Права проверяет вызывающий use case. */
@Component
class GradebookTableBuilder {

    private static final Set<CourseRole> STUDENT_ROLES = Set.of(CourseRole.STUDENT);

    private final EnrollmentApi enrollment;
    private final UsersApi users;
    private final CourseGradebooks gradebooks;
    private final GradeRepository grades;

    GradebookTableBuilder(EnrollmentApi enrollment, UsersApi users, CourseGradebooks gradebooks, GradeRepository grades) {
        this.enrollment = enrollment;
        this.users = users;
        this.gradebooks = gradebooks;
        this.grades = grades;
    }

    Gradebook build(UUID tenantId, UUID courseId, UUID groupId) {
        CourseGradebook gradebook = gradebooks.load(tenantId, courseId);
        List<UUID> studentIds = students(tenantId, courseId, groupId);
        Map<UUID, UserRef> people = users.findAll(tenantId, studentIds);
        Map<UUID, List<Grade>> byUser = grades.ofColumns(tenantId, gradebook.columnIds()).stream()
                .collect(Collectors.groupingBy(Grade::userId));
        List<Row> rows = studentIds.stream()
                .filter(people::containsKey)
                .map(people::get)
                .sorted(Comparator.comparing(UserRef::lastName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(UserRef::firstName, String.CASE_INSENSITIVE_ORDER))
                .map(person -> row(gradebook, person, byUser.getOrDefault(person.id(), List.of())))
                .toList();
        return new Gradebook(gradebook.columns().stream().map(Column::of).toList(), rows);
    }

    private List<UUID> students(UUID tenantId, UUID courseId, UUID groupId) {
        List<UUID> all = enrollment.activeMembers(tenantId, courseId, STUDENT_ROLES).stream()
                .map(EnrollmentApi.Member::userId).toList();
        if (groupId == null) {
            return all;
        }
        Set<UUID> groupMembers = enrollment.membersOfGroups(tenantId, courseId, Set.of(groupId));
        return all.stream().filter(groupMembers::contains).toList();
    }

    private static Row row(CourseGradebook gradebook, UserRef person, List<Grade> userGrades) {
        Map<UUID, Cell> cells = new HashMap<>();
        gradebook.columns().forEach(column -> cells.put(column.id(), Cell.EMPTY));
        userGrades.forEach(grade -> cells.put(grade.columnId(), Cell.of(grade)));
        BigDecimal percent = gradebook.finalPercent(PublishedScores.all(userGrades)).orElse(null);
        return new Row(person.id(), person.displayName(), cells, percent, gradebook.label(percent));
    }
}
