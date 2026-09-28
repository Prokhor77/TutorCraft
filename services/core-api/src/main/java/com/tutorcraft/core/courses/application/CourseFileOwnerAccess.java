package com.tutorcraft.core.courses.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.courses.domain.Course;
import com.tutorcraft.core.files.spi.FileOwnerAccess;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Файлы курса (обложка, файлы описания): опубликованный курс — витрина, его файлы видит любой пользователь tenant;
 * иначе требуется course.view.
 */
@Component
class CourseFileOwnerAccess implements FileOwnerAccess {

    private final CourseRepository courses;
    private final AccessService access;
    private final Clock clock;

    CourseFileOwnerAccess(CourseRepository courses, AccessService access, Clock clock) {
        this.courses = courses;
        this.access = access;
        this.clock = clock;
    }

    @Override
    public String ownerType() {
        return CourseContentFiles.OWNER_COURSE;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canRead(UUID tenantId, UUID userId, UUID ownerId) {
        Optional<Course> course = courses.find(tenantId, ownerId);
        if (course.isEmpty()) {
            return false;
        }
        if (course.get().visibleToLearnersAt(clock.instant())) {
            return true;
        }
        return access.permissionsOf(tenantId, userId, AccessContext.course(ownerId)).contains(Permission.COURSE_VIEW);
    }
}
