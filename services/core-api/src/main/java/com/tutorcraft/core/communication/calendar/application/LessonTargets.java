package com.tutorcraft.core.communication.calendar.application;

import com.tutorcraft.core.access.domain.CourseRole;
import com.tutorcraft.core.communication.calendar.domain.CalendarRules;
import com.tutorcraft.core.courses.CoursesApi;
import com.tutorcraft.core.courses.ItemRef;
import com.tutorcraft.core.courses.ModuleRef;
import com.tutorcraft.core.enrollment.EnrollmentApi;
import com.tutorcraft.core.enrollment.EnrollmentApi.Member;
import com.tutorcraft.core.shared.domain.Validator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Проверка привязок занятия: модуль и элемент принадлежат курсу, ученики — активные ученики курса. */
@Component
class LessonTargets {

    private static final int MAX_ATTENDEES = CalendarRules.MAX_ATTENDEES;
    private static final Set<CourseRole> STUDENTS = Set.of(CourseRole.STUDENT);
    private static final String NOT_IN_COURSE = "not_in_course";

    private final CoursesApi courses;
    private final EnrollmentApi enrollment;

    LessonTargets(CoursesApi courses, EnrollmentApi enrollment) {
        this.courses = courses;
        this.enrollment = enrollment;
    }

    /** Активные ученики курса. */
    Set<UUID> activeStudents(UUID tenantId, UUID courseId) {
        return enrollment.activeMembers(tenantId, courseId, STUDENTS).stream().map(Member::userId)
                .collect(Collectors.toSet());
    }

    void validate(Validator validator, UUID tenantId, UUID courseId, LessonDraft draft) {
        validator.check(draft.moduleId() == null || moduleInCourse(tenantId, courseId, draft.moduleId()), "moduleId",
                        NOT_IN_COURSE, "Module does not belong to the course")
                .check(draft.itemId() == null || itemMatches(tenantId, courseId, draft), "itemId", NOT_IN_COURSE,
                        "Item does not belong to the course or module")
                .check(draft.attendeeIds().size() <= MAX_ATTENDEES, "attendeeIds", "too_many",
                        "At most " + MAX_ATTENDEES + " students")
                .check(activeStudents(tenantId, courseId).containsAll(draft.attendeeIds()), "attendeeIds",
                        "not_students", "Only active students of the course can be invited");
    }

    private boolean moduleInCourse(UUID tenantId, UUID courseId, UUID moduleId) {
        List<ModuleRef> modules = courses.modulesOfCourse(tenantId, courseId);
        return modules.stream().anyMatch(module -> module.id().equals(moduleId));
    }

    private boolean itemMatches(UUID tenantId, UUID courseId, LessonDraft draft) {
        ItemRef item = courses.findItems(tenantId, List.of(draft.itemId())).get(draft.itemId());
        return item != null && item.courseId().equals(courseId)
                && (draft.moduleId() == null || draft.moduleId().equals(item.moduleId()));
    }
}
