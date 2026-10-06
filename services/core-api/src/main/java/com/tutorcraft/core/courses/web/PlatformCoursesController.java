package com.tutorcraft.core.courses.web;

import com.tutorcraft.core.courses.application.PlatformCoursesService;
import com.tutorcraft.core.courses.application.PlatformCoursesService.PlatformCourseView;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.api.PageResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Курсы всех школ — эндпоинт главного администратора платформы. */
@RestController
@RequestMapping("/api/v1/platform/courses")
class PlatformCoursesController {

    private final PlatformCoursesService courses;

    PlatformCoursesController(PlatformCoursesService courses) {
        this.courses = courses;
    }

    @GetMapping
    PageResponse<PlatformCourseView> list(@RequestParam(required = false) UUID tenantId,
                                          @RequestParam(required = false) String q,
                                          @RequestParam(required = false) String cursor,
                                          @RequestParam(required = false) Integer limit) {
        return courses.list(tenantId, q, PageQuery.of(cursor, limit));
    }
}
