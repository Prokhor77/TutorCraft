package com.tutorcraft.core.communication.calendar.web;

import com.tutorcraft.core.communication.calendar.application.LessonDraft;
import com.tutorcraft.core.communication.calendar.application.LessonService;
import com.tutorcraft.core.communication.calendar.domain.CalendarRules;
import com.tutorcraft.core.identity.UsersApi.UserRef;
import com.tutorcraft.core.shared.api.IfMatch;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Занятия курсов в календаре (контракт §2): репетитор назначает занятие всему курсу или выбранным ученикам. */
@RestController
@RequestMapping("/api/v1")
class LessonsController {

    private static final int MAX_TITLE = CalendarRules.MAX_TITLE;
    private static final int MAX_DESCRIPTION = CalendarRules.MAX_DESCRIPTION;
    private static final int MAX_ATTENDEES = CalendarRules.MAX_ATTENDEES;

    private final LessonService lessons;

    LessonsController(LessonService lessons) {
        this.lessons = lessons;
    }

    @GetMapping("/me/calendar/lesson-courses")
    List<CourseOption> courses() {
        return lessons.schedulableCourses().stream().map(course -> new CourseOption(course.id(), course.title())).toList();
    }

    @GetMapping("/courses/{courseId}/calendar/students")
    List<StudentOption> students(@PathVariable UUID courseId) {
        return lessons.students(courseId).stream().map(StudentOption::of).toList();
    }

    @PostMapping("/courses/{courseId}/calendar/lessons")
    @ResponseStatus(HttpStatus.CREATED)
    EventView create(@PathVariable UUID courseId, @Valid @RequestBody LessonRequest request) {
        return EventView.of(lessons.create(courseId, request.toDraft()));
    }

    @PutMapping("/courses/{courseId}/calendar/lessons/{lessonId}")
    EventView update(@PathVariable UUID courseId, @PathVariable UUID lessonId,
                     @RequestHeader(value = IfMatch.HEADER, required = false) String ifMatch,
                     @Valid @RequestBody LessonRequest request) {
        long version = IfMatch.resolve(ifMatch, request.version());
        return EventView.of(lessons.update(courseId, lessonId, version, request.toDraft()));
    }

    @DeleteMapping("/courses/{courseId}/calendar/lessons/{lessonId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID courseId, @PathVariable UUID lessonId) {
        lessons.delete(courseId, lessonId);
    }

    /** attendeeIds пуст или отсутствует — занятие для всех учеников курса. */
    record LessonRequest(@NotBlank @Size(max = MAX_TITLE) String title, @Size(max = MAX_DESCRIPTION) String description,
                         @NotNull Instant startsAt, Instant endsAt, UUID moduleId, UUID itemId,
                         @Size(max = MAX_ATTENDEES) List<@NotNull UUID> attendeeIds, Long version) {

        LessonDraft toDraft() {
            return new LessonDraft(title, description, startsAt, endsAt, moduleId, itemId, attendeeIds);
        }
    }

    record CourseOption(UUID id, String title) {
    }

    record StudentOption(UUID id, String firstName, String lastName, String email) {

        static StudentOption of(UserRef user) {
            return new StudentOption(user.id(), user.firstName(), user.lastName(), user.email());
        }
    }
}
