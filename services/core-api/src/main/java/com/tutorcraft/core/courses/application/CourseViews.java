package com.tutorcraft.core.courses.application;

import com.tutorcraft.core.access.domain.CourseRole;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.courses.domain.Course;
import com.tutorcraft.core.courses.domain.SelfEnrolSettings;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Отображение курса в Course / CourseCard (контракт §5). */
@Component
class CourseViews {

    private final CourseContentFiles files;

    CourseViews(CourseContentFiles files) {
        this.files = files;
    }

    CourseView course(Course course, Set<Permission> permissions, CourseRole role) {
        return new CourseView(course.id(), course.title(), course.shortName(), course.slug(), course.categoryId(),
                course.description(), course.coverFileId(), coverUrl(course), course.startsAt(), course.endsAt(),
                course.visibility().key(), course.publishAt(), selfEnrolFor(course, permissions),
                course.completionRule(), course.groupMode().key(), role == null ? null : role.key(),
                permissionKeys(permissions), course.version());
    }

    CourseCardView card(Course course, CourseRole role, Integer progressPercent) {
        return new CourseCardView(course.id(), course.title(), course.shortName(), coverUrl(course), course.categoryId(),
                role == null ? null : role.key(), progressPercent, course.visibility().key());
    }

    String coverUrl(Course course) {
        return files.downloadUrl(course.tenantId(), course.coverFileId()).orElse(null);
    }

    static List<String> permissionKeys(Set<Permission> permissions) {
        return permissions.stream().map(Permission::key).sorted().toList();
    }

    /** Код самозаписи — секрет преподавателя: скрыт от тех, кто не управляет записью. */
    private static SelfEnrolSettings selfEnrolFor(Course course, Set<Permission> permissions) {
        SelfEnrolSettings settings = course.selfEnrol();
        if (permissions.contains(Permission.ENROLLMENT_MANAGE) || !settings.hasCode()) {
            return settings;
        }
        return new SelfEnrolSettings(settings.enabled(), null, settings.maxStudents(), settings.until());
    }
}
