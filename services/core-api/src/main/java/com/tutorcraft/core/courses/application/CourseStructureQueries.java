package com.tutorcraft.core.courses.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.courses.Availability;
import com.tutorcraft.core.courses.domain.Course;
import com.tutorcraft.core.courses.domain.CourseItem;
import com.tutorcraft.core.shared.domain.ForbiddenException;
import com.tutorcraft.core.shared.domain.NotFoundException;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Оглавление курса и открытие элемента с учётом прав (FR-COURSE-05, FR-PROG-03, UX-07): персонал видит всё,
 * учащийся — только видимое; недоступное показывается с причинами или скрывается.
 */
@Service
public class CourseStructureQueries {

    private static final String REASONS = "reasons";

    private final CourseRepository courses;
    private final ItemRepository items;
    private final AccessService access;
    private final CurrentUserProvider currentUser;
    private final LearnerStateResolver resolver;
    private final OutlineAssembler outlines;
    private final ItemViews itemViews;
    private final CourseChangeEvents events;
    private final Clock clock;

    public CourseStructureQueries(CourseRepository courses, ItemRepository items, AccessService access,
                                  CurrentUserProvider currentUser, LearnerStateResolver resolver, OutlineAssembler outlines,
                                  ItemViews itemViews, CourseChangeEvents events, Clock clock) {
        this.courses = courses;
        this.items = items;
        this.access = access;
        this.currentUser = currentUser;
        this.resolver = resolver;
        this.outlines = outlines;
        this.itemViews = itemViews;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(noRollbackFor = NotFoundException.class)
    public OutlineView outline(UUID courseId) {
        CurrentUser user = currentUser.require();
        Set<Permission> permissions = access.permissions(AccessContext.course(courseId));
        PermissionChecks.require(permissions, Permission.COURSE_VIEW);
        Course course = courses.find(user.tenantId(), courseId).orElseThrow(CoursesErrors::courseNotFound);
        if (PermissionChecks.isStaff(permissions)) {
            return outlines.outline(courseId, resolver.forStaff(course));
        }
        requireVisibleCourse(course);
        return outlines.outline(courseId, resolver.forLearner(course, user.userId()));
    }

    /** Учащемуся недоступный элемент → 403 item.locked (args.reasons), скрытый → 404; открытие — событие ItemViewed. */
    @Transactional
    public ItemView item(UUID itemId) {
        CurrentUser user = currentUser.require();
        CourseItem item = items.find(user.tenantId(), itemId).orElseThrow(CoursesErrors::itemNotFound);
        Set<Permission> permissions = access.permissions(AccessContext.course(item.courseId()));
        List<String> keys = CourseViews.permissionKeys(permissions);
        if (PermissionChecks.isStaff(permissions)) {
            return itemViews.staff(item, keys);
        }
        PermissionChecks.require(permissions, Permission.CONTENT_VIEW);
        Course course = courses.find(user.tenantId(), item.courseId()).orElseThrow(CoursesErrors::courseNotFound);
        requireVisibleCourse(course);
        LearnerSnapshot snapshot = resolver.forLearner(course, user.userId());
        if (!snapshot.contains(item)) {
            throw CoursesErrors.itemNotFound();
        }
        Availability availability = snapshot.effectiveAvailability(item);
        if (!availability.available()) {
            throw new ForbiddenException(CoursesErrors.ITEM_LOCKED, "Item is not available yet",
                    Map.of(REASONS, availability.reasons()));
        }
        events.itemViewed(item, user.userId());
        return itemViews.learner(item, availability, snapshot.completionOf(itemId), snapshot.statusOf(itemId), keys);
    }

    private void requireVisibleCourse(Course course) {
        if (!course.visibleToLearnersAt(clock.instant())) {
            throw new ForbiddenException(CoursesErrors.COURSE_HIDDEN, "Course is not available yet");
        }
    }
}
