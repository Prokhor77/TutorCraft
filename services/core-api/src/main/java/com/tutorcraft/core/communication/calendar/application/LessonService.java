package com.tutorcraft.core.communication.calendar.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.communication.calendar.domain.CalendarEvent;
import com.tutorcraft.core.communication.calendar.domain.CalendarRules;
import com.tutorcraft.core.communication.calendar.domain.Lesson;
import com.tutorcraft.core.courses.CourseRef;
import com.tutorcraft.core.courses.CoursesApi;
import com.tutorcraft.core.identity.UsersApi;
import com.tutorcraft.core.identity.UsersApi.UserRef;
import com.tutorcraft.core.shared.api.IfMatch;
import com.tutorcraft.core.shared.domain.ConflictException;
import com.tutorcraft.core.shared.domain.Ids;
import com.tutorcraft.core.shared.domain.NotFoundException;
import com.tutorcraft.core.shared.domain.Validator;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Занятия репетитора в календаре курса (course.edit): назначение всему курсу или выбранным ученикам. */
@Service
public class LessonService {

    private static final Logger log = LoggerFactory.getLogger(LessonService.class);
    private static final String NOT_FOUND = "calendar.lesson_not_found";

    private final CurrentUserProvider currentUser;
    private final AccessService access;
    private final LessonRepository lessons;
    private final LessonTargets targets;
    private final LessonEventsReader reader;
    private final LessonNotifier notifier;
    private final UserCoursesResolver userCourses;
    private final CoursesApi courses;
    private final UsersApi users;
    private final Clock clock;

    LessonService(CurrentUserProvider currentUser, AccessService access, LessonRepository lessons, LessonTargets targets,
                  LessonEventsReader reader, LessonNotifier notifier, UserCoursesResolver userCourses, CoursesApi courses,
                  UsersApi users, Clock clock) {
        this.currentUser = currentUser;
        this.access = access;
        this.lessons = lessons;
        this.targets = targets;
        this.reader = reader;
        this.notifier = notifier;
        this.userCourses = userCourses;
        this.courses = courses;
        this.users = users;
        this.clock = clock;
    }

    /** Курсы, в которых текущий пользователь может назначать занятия. */
    @Transactional(readOnly = true)
    public List<CourseRef> schedulableCourses() {
        CurrentUser user = currentUser.require();
        Set<UUID> editable = userCourses.resolve(user.tenantId(), user.userId()).editable();
        return courses.findCourses(user.tenantId(), editable).values().stream()
                .sorted(Comparator.comparing(CourseRef::title, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Активные ученики курса — кандидаты в участники занятия. */
    @Transactional(readOnly = true)
    public List<UserRef> students(UUID courseId) {
        CurrentUser user = requireEditor(courseId);
        Set<UUID> ids = targets.activeStudents(user.tenantId(), courseId);
        return users.findAll(user.tenantId(), ids).values().stream()
                .sorted(Comparator.comparing(UserRef::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional
    public CalendarEvent create(UUID courseId, LessonDraft draft) {
        CurrentUser user = requireEditor(courseId);
        validate(user.tenantId(), courseId, draft);
        Instant now = clock.instant();
        Lesson lesson = toLesson(Ids.newId(), user.tenantId(), courseId, draft, user.userId(), 0, now, now);
        lessons.insert(lesson);
        notifier.created(lesson, targets.activeStudents(user.tenantId(), courseId));
        log.info("Lesson {} scheduled in course {}", lesson.id(), courseId);
        return reader.toStaffEvent(user.tenantId(), lesson);
    }

    @Transactional
    public CalendarEvent update(UUID courseId, UUID lessonId, long expectedVersion, LessonDraft draft) {
        CurrentUser user = requireEditor(courseId);
        Lesson current = requireLesson(user.tenantId(), courseId, lessonId);
        IfMatch.check(expectedVersion, current.version());
        validate(user.tenantId(), courseId, draft);
        Lesson updated = toLesson(lessonId, user.tenantId(), courseId, draft, current.createdBy(), current.version() + 1,
                current.createdAt(), clock.instant());
        if (!lessons.update(updated, expectedVersion)) {
            throw new ConflictException(IfMatch.VERSION_CONFLICT_CODE, "Lesson was modified by someone else");
        }
        notifier.updated(current, updated, targets.activeStudents(user.tenantId(), courseId));
        return reader.toStaffEvent(user.tenantId(), updated);
    }

    @Transactional
    public void delete(UUID courseId, UUID lessonId) {
        CurrentUser user = requireEditor(courseId);
        Lesson lesson = requireLesson(user.tenantId(), courseId, lessonId);
        lessons.delete(user.tenantId(), lessonId);
        notifier.deleted(lesson, targets.activeStudents(user.tenantId(), courseId));
        log.info("Lesson {} cancelled in course {}", lessonId, courseId);
    }

    private CurrentUser requireEditor(UUID courseId) {
        CurrentUser user = currentUser.require();
        access.require(Permission.COURSE_EDIT, AccessContext.course(courseId));
        return user;
    }

    private Lesson requireLesson(UUID tenantId, UUID courseId, UUID lessonId) {
        return lessons.find(tenantId, courseId, lessonId)
                .orElseThrow(() -> new NotFoundException(NOT_FOUND, "Lesson not found"));
    }

    private void validate(UUID tenantId, UUID courseId, LessonDraft draft) {
        Validator validator = CalendarRules.validate(draft.title(), draft.description(), draft.startsAt(), draft.endsAt());
        targets.validate(validator, tenantId, courseId, draft);
        validator.throwIfInvalid();
    }

    private static Lesson toLesson(UUID id, UUID tenantId, UUID courseId, LessonDraft draft, UUID createdBy, long version,
                                   Instant createdAt, Instant updatedAt) {
        return new Lesson(id, tenantId, courseId, draft.moduleId(), draft.itemId(), draft.title().strip(),
                CalendarRules.normalizeText(draft.description()), draft.startsAt(), draft.endsAt(), draft.audience(),
                draft.attendeeIds(), createdBy, version, createdAt, updatedAt);
    }
}
