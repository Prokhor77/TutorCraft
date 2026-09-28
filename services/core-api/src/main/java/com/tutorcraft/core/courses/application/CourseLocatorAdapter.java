package com.tutorcraft.core.courses.application;

import com.tutorcraft.core.access.spi.CourseLocator;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Порт access: существование курса в tenant и его категория. Только репозиторий (правило против циклов бинов). */
@Component
class CourseLocatorAdapter implements CourseLocator {

    private final CourseRepository courses;

    CourseLocatorAdapter(CourseRepository courses) {
        this.courses = courses;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Optional<UUID>> categoryOf(UUID tenantId, UUID courseId) {
        return courses.categoryOf(tenantId, courseId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsInOtherTenant(UUID tenantId, UUID courseId) {
        return courses.existsInOtherTenant(tenantId, courseId);
    }
}
