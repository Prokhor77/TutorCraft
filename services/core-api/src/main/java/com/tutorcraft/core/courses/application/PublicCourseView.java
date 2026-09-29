package com.tutorcraft.core.courses.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** PublicCourse (контракт §13): лендинг/каталог без авторизации (FR-COURSE-HYB-01). */
public record PublicCourseView(UUID id, String slug, String title, Map<String, Object> description, String coverUrl,
                               Teacher teacher, List<ModuleSummary> modules, boolean selfEnrolEnabled,
                               String tenantSlug, String tenantName) {

    public record Teacher(String name, String avatarUrl) {
    }

    public record ModuleSummary(String title, int itemCount) {
    }

    /** Обёртка списка для кэша (Jackson не восстанавливает generic-списки по Class). */
    public record Catalog(List<PublicCourseView> courses) {
    }
}
