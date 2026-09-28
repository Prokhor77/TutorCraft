package com.tutorcraft.core.dashboard.web;

import com.tutorcraft.core.dashboard.application.DashboardViews.CourseCard;
import com.tutorcraft.core.dashboard.application.DashboardViews.MyTasks;
import com.tutorcraft.core.dashboard.application.DashboardViews.TeacherHome;
import com.tutorcraft.core.dashboard.application.MyCoursesService;
import com.tutorcraft.core.dashboard.application.MyTasksService;
import com.tutorcraft.core.dashboard.application.TeacherHomeService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Главные страницы (контракт §2: /me/tasks, /me/teaching, /me/courses). */
@RestController
@RequestMapping("/api/v1/me")
class DashboardController {

    private final MyTasksService tasks;
    private final TeacherHomeService teaching;
    private final MyCoursesService myCourses;

    DashboardController(MyTasksService tasks, TeacherHomeService teaching, MyCoursesService myCourses) {
        this.tasks = tasks;
        this.teaching = teaching;
        this.myCourses = myCourses;
    }

    @GetMapping("/tasks")
    MyTasks tasks() {
        return tasks.tasks();
    }

    @GetMapping("/teaching")
    TeacherHome teaching() {
        return teaching.home();
    }

    @GetMapping("/courses")
    List<CourseCard> courses() {
        return myCourses.courses();
    }
}
