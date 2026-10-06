package com.tutorcraft.core.courses.web;

import com.tutorcraft.core.courses.application.CourseStorageService;
import com.tutorcraft.core.courses.application.CourseStorageService.CourseStorageView;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Место, занятое материалами курсов школы, — эндпоинт главного администратора платформы. */
@RestController
@RequestMapping("/api/v1/platform/storage")
class PlatformCourseStorageController {

    private final CourseStorageService storage;

    PlatformCourseStorageController(CourseStorageService storage) {
        this.storage = storage;
    }

    @GetMapping("/{tenantId}/courses")
    List<CourseStorageView> byCourse(@PathVariable UUID tenantId) {
        return storage.byCourse(tenantId);
    }
}
