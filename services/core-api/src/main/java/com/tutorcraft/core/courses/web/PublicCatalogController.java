package com.tutorcraft.core.courses.web;

import com.tutorcraft.core.courses.application.PublicCatalogService;
import com.tutorcraft.core.courses.application.PublicCourseView;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Публичная витрина для SSR-лендингов (FR-COURSE-HYB-01), без авторизации (/api/v1/public/** в SecurityConfig). */
@RestController
@RequestMapping("/api/v1/public/{tenantSlug}/courses")
class PublicCatalogController {

    private final PublicCatalogService catalog;

    PublicCatalogController(PublicCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    List<PublicCourseView> list(@PathVariable String tenantSlug) {
        return catalog.catalog(tenantSlug);
    }

    @GetMapping("/{courseSlug}")
    PublicCourseView course(@PathVariable String tenantSlug, @PathVariable String courseSlug) {
        return catalog.course(tenantSlug, courseSlug);
    }
}
