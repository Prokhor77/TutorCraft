package com.tutorcraft.core.courses.application;

import com.tutorcraft.core.access.domain.CourseRole;
import com.tutorcraft.core.access.spi.CategoryAncestry;
import com.tutorcraft.core.courses.CourseEvents.ChangeKind;
import com.tutorcraft.core.courses.domain.Course;
import com.tutorcraft.core.courses.domain.CourseSlugs;
import com.tutorcraft.core.enrollment.EnrollmentApi;
import com.tutorcraft.core.enrollment.EnrollmentApi.EnrolCommand;
import com.tutorcraft.core.shared.api.IfMatch;
import com.tutorcraft.core.shared.domain.ConflictException;
import com.tutorcraft.core.shared.domain.ValidationException;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Запись курса с проверкой инвариантов, которые требуют хранилища: краткое имя, категория, обложка, файлы описания,
 * обязательные элементы правила завершения. Автор нового курса записывается преподавателем.
 */
@Component
class CourseWriter {

    private static final String FIELD_SHORT_NAME = "shortName";
    private static final String FIELD_CATEGORY = "categoryId";
    private static final String FIELD_COVER = "coverFileId";
    private static final String FIELD_REQUIRED_ITEMS = "completionRule.requiredItemIds";

    private final CourseRepository courses;
    private final ItemRepository items;
    private final CategoryAncestry categories;
    private final CourseContentFiles files;
    private final EnrollmentApi enrollment;
    private final CourseChangeEvents events;
    private final Clock clock;

    CourseWriter(CourseRepository courses, ItemRepository items, CategoryAncestry categories, CourseContentFiles files,
                 EnrollmentApi enrollment, CourseChangeEvents events, Clock clock) {
        this.courses = courses;
        this.items = items;
        this.categories = categories;
        this.files = files;
        this.enrollment = enrollment;
        this.events = events;
        this.clock = clock;
    }

    /** Новый курс + запись автора преподавателем + аудит + событие CREATED. */
    void insertNew(Course course, UUID actorId) {
        course.validate();
        requireCategory(course.tenantId(), course.categoryId());
        requireShortNameFree(course);
        requireCover(course);
        courses.insert(course);
        linkFiles(course);
        enrollment.enrol(new EnrolCommand(course.tenantId(), course.id(), actorId, CourseRole.TEACHER,
                EnrolCommand.METHOD_MANUAL, actorId));
        events.audit(course.tenantId(), actorId, CourseChangeEvents.OBJECT_COURSE, course.id(), "created", course.id(), null);
        events.courseChanged(course.tenantId(), course.id(), ChangeKind.CREATED, actorId);
    }

    /** Оптимистичная запись изменённого курса; проверки выполняются только для изменившихся полей. */
    void save(Course before, Course after, long expectedVersion) {
        after.validate();
        if (!Objects.equals(before.categoryId(), after.categoryId())) {
            requireCategory(after.tenantId(), after.categoryId());
        }
        if (!Objects.equals(before.shortName(), after.shortName())) {
            requireShortNameFree(after);
        }
        if (!Objects.equals(before.coverFileId(), after.coverFileId())) {
            requireCover(after);
        }
        requireCompletionItems(after);
        if (!courses.update(after, expectedVersion, clock.instant())) {
            throw new ConflictException(IfMatch.VERSION_CONFLICT_CODE, "Course was modified by someone else");
        }
        linkFiles(after);
    }

    String uniqueSlug(UUID tenantId, String title) {
        String base = CourseSlugs.slugify(title);
        return CourseSlugs.unique(base, courses.slugsStartingWith(tenantId, base));
    }

    private void requireCategory(UUID tenantId, UUID categoryId) {
        if (categoryId != null && categories.selfAndAncestors(tenantId, categoryId).isEmpty()) {
            throw ValidationException.single(FIELD_CATEGORY, "not_found", "Category not found");
        }
    }

    private void requireShortNameFree(Course course) {
        if (course.shortName() != null && courses.shortNameTaken(course.tenantId(), course.shortName(), course.id())) {
            throw new ConflictException(CoursesErrors.SHORT_NAME_TAKEN, "Short name is already used",
                    Map.of(FIELD_SHORT_NAME, course.shortName()));
        }
    }

    private void requireCover(Course course) {
        if (course.coverFileId() != null) {
            files.requireReady(course.tenantId(), List.of(course.coverFileId()), FIELD_COVER);
        }
    }

    private void requireCompletionItems(Course course) {
        List<UUID> required = course.completionRule().requiredItemIds();
        if (required.isEmpty()) {
            return;
        }
        Set<UUID> ofCourse = new HashSet<>();
        items.findAll(course.tenantId(), required).values().stream()
                .filter(item -> item.courseId().equals(course.id()))
                .forEach(item -> ofCourse.add(item.id()));
        if (!ofCourse.containsAll(required)) {
            throw ValidationException.single(FIELD_REQUIRED_ITEMS, "not_in_course", "Required items must belong to the course");
        }
    }

    private void linkFiles(Course course) {
        Set<UUID> fileIds = new HashSet<>(CourseContentFiles.docFileIds(course.description()));
        if (course.coverFileId() != null) {
            fileIds.add(course.coverFileId());
        }
        files.link(course.tenantId(), fileIds, CourseContentFiles.OWNER_COURSE, course.id());
    }
}
