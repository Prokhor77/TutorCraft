package com.tutorcraft.core.assessment.assignment.application;

import com.tutorcraft.core.access.domain.CourseRole;
import com.tutorcraft.core.assessment.assignment.domain.AssignmentErrors;
import com.tutorcraft.core.courses.CourseRef;
import com.tutorcraft.core.courses.CoursesApi;
import com.tutorcraft.core.enrollment.EnrollmentApi;
import com.tutorcraft.core.shared.domain.NotFoundException;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Ограничение ассистента группами: в курсе с режимом групп «separate» ассистент видит и проверяет
 * только работы участников своих групп. Для остальных ролей ограничения нет.
 */
@Component
class GraderScope {

    private static final String SEPARATE_GROUPS = "separate";

    private final CoursesApi courses;
    private final EnrollmentApi enrollment;

    GraderScope(CoursesApi courses, EnrollmentApi enrollment) {
        this.courses = courses;
        this.enrollment = enrollment;
    }

    /** Пусто — без ограничений; иначе — множество студентов, доступных проверяющему. */
    Optional<Set<UUID>> allowedStudents(UUID tenantId, UUID courseId, UUID graderId) {
        Optional<EnrollmentApi.Member> membership = enrollment.membership(tenantId, courseId, graderId);
        if (membership.isEmpty() || membership.get().role() != CourseRole.ASSISTANT) {
            return Optional.empty();
        }
        CourseRef course = courses.requireCourse(tenantId, courseId);
        if (!SEPARATE_GROUPS.equals(course.groupMode())) {
            return Optional.empty();
        }
        Set<UUID> groups = membership.get().groupIds();
        return Optional.of(groups.isEmpty() ? Set.of() : enrollment.membersOfGroups(tenantId, courseId, groups));
    }

    /** Чужая для ассистента работа — 404, как и несуществующая (не раскрываем существование). */
    void requireVisible(Optional<Set<UUID>> allowed, Collection<UUID> members) {
        if (allowed.isPresent() && members.stream().noneMatch(allowed.get()::contains)) {
            throw new NotFoundException(AssignmentErrors.SUBMISSION_NOT_FOUND, "Submission not found");
        }
    }
}
