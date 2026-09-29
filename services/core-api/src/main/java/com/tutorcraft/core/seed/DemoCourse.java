package com.tutorcraft.core.seed;

import com.tutorcraft.core.access.domain.CourseRole;
import com.tutorcraft.core.assessment.quiz.application.QuestionBankService;
import com.tutorcraft.core.assessment.quiz.application.QuizLayoutService;
import com.tutorcraft.core.assessment.quiz.domain.LayoutSlot;
import com.tutorcraft.core.courses.application.CourseCommandService;
import com.tutorcraft.core.courses.application.CourseCommands.CoursePatch;
import com.tutorcraft.core.courses.application.CourseCommands.CreateCourse;
import com.tutorcraft.core.courses.application.CourseCommands.CreateItem;
import com.tutorcraft.core.courses.application.CourseCommands.ItemPatch;
import com.tutorcraft.core.courses.application.CourseQueryService;
import com.tutorcraft.core.courses.application.CourseView;
import com.tutorcraft.core.courses.application.ItemCommandService;
import com.tutorcraft.core.courses.application.ItemView;
import com.tutorcraft.core.courses.application.ModuleService;
import com.tutorcraft.core.courses.domain.Patch;
import com.tutorcraft.core.enrollment.EnrollmentApi;
import com.tutorcraft.core.enrollment.EnrollmentApi.EnrolCommand;
import com.tutorcraft.core.seed.DemoAccounts.Accounts;
import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Демо-курс: 2 модуля, страница, задание со сроком, тест из 5 вопросов, форум; студенты записаны (от имени преподавателя). */
@Component
class DemoCourse {

    private static final String PUBLISHED = "published";
    private static final Duration ASSIGNMENT_DUE_IN = Duration.ofDays(7);
    private static final String DUE_AT = "dueAt";

    private final CourseCommandService courses;
    private final CourseQueryService courseQueries;
    private final ModuleService modules;
    private final ItemCommandService items;
    private final QuestionBankService questionBank;
    private final QuizLayoutService quizLayouts;
    private final EnrollmentApi enrollment;
    private final Clock clock;

    DemoCourse(CourseCommandService courses, CourseQueryService courseQueries, ModuleService modules, ItemCommandService items,
               QuestionBankService questionBank, QuizLayoutService quizLayouts, EnrollmentApi enrollment, Clock clock) {
        this.courses = courses;
        this.courseQueries = courseQueries;
        this.modules = modules;
        this.items = items;
        this.questionBank = questionBank;
        this.quizLayouts = quizLayouts;
        this.enrollment = enrollment;
        this.clock = clock;
    }

    void create(Accounts accounts) {
        CourseView course = courses.create(new CreateCourse(DemoContent.COURSE_TITLE, DemoContent.COURSE_SHORT_NAME, null,
                DemoContent.doc("Демонстрационный курс с материалами, заданием, тестом и форумом."), null, null, null));
        UUID intro = modules.create(course.id(), DemoContent.MODULE_INTRO, null).id();
        UUID practice = modules.create(course.id(), DemoContent.MODULE_PRACTICE, null).id();
        addWelcomePage(intro);
        items.create(intro, new CreateItem("forum", DemoContent.FORUM_TITLE, null));
        items.create(practice, new CreateItem("assignment", DemoContent.ASSIGNMENT_TITLE, Map.of(DUE_AT, assignmentDueAt())));
        addQuiz(course.id(), practice);
        publish(course.id());
        enrolStudents(accounts, course.id());
    }

    private void addWelcomePage(UUID moduleId) {
        ItemView page = items.create(moduleId, new CreateItem("page", DemoContent.PAGE_TITLE, null));
        items.update(page.id(), new ItemPatch(Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(),
                Patch.of(DemoContent.welcomePage()), Patch.absent(), Patch.absent()), page.version());
    }

    private void addQuiz(UUID courseId, UUID moduleId) {
        ItemView quiz = items.create(moduleId, new CreateItem("quiz", DemoContent.QUIZ_TITLE, null));
        List<LayoutSlot> slots = DemoContent.quizQuestions().stream()
                .<LayoutSlot>map(draft -> new LayoutSlot.Fixed(questionBank.create(courseId, draft).id(), null, null))
                .toList();
        quizLayouts.replace(quiz.id(), slots);
    }

    /** Версия перечитывается: добавление структуры может менять строку курса. */
    private void publish(UUID courseId) {
        CourseView course = courseQueries.get(courseId);
        courses.update(courseId, new CoursePatch(Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(),
                Patch.absent(), Patch.absent(), Patch.absent(), Patch.of(PUBLISHED), Patch.absent(), Patch.absent(),
                Patch.absent(), Patch.absent()), course.version());
    }

    private void enrolStudents(Accounts accounts, UUID courseId) {
        accounts.studentIds().forEach(studentId -> enrollment.enrol(new EnrolCommand(accounts.tenantId(), courseId, studentId,
                CourseRole.STUDENT, EnrolCommand.METHOD_MANUAL, accounts.teacherId())));
    }

    private String assignmentDueAt() {
        return clock.instant().plus(ASSIGNMENT_DUE_IN).truncatedTo(ChronoUnit.HOURS).toString();
    }
}
