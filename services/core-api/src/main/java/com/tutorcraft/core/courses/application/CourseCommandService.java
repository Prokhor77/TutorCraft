package com.tutorcraft.core.courses.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.CourseRole;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.courses.CourseEvents.ChangeKind;
import com.tutorcraft.core.courses.Visibility;
import com.tutorcraft.core.courses.application.CourseCommands.CoursePatch;
import com.tutorcraft.core.courses.application.CourseCommands.CreateCourse;
import com.tutorcraft.core.courses.domain.Course;
import com.tutorcraft.core.courses.domain.CourseCompletionRule;
import com.tutorcraft.core.courses.domain.CourseTexts;
import com.tutorcraft.core.courses.domain.GroupMode;
import com.tutorcraft.core.courses.domain.SelfEnrolSettings;
import com.tutorcraft.core.courses.domain.TrashPolicy;
import com.tutorcraft.core.enrollment.EnrollmentApi;
import com.tutorcraft.core.shared.api.IfMatch;
import com.tutorcraft.core.shared.config.AppProperties;
import com.tutorcraft.core.shared.content.BlockDocs.SanitizedDoc;
import com.tutorcraft.core.shared.domain.BusinessRuleException;
import com.tutorcraft.core.shared.domain.ForbiddenException;
import com.tutorcraft.core.shared.domain.Ids;
import com.tutorcraft.core.shared.domain.Money;
import com.tutorcraft.core.shared.domain.ValidationException;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Создание, правка, цена, корзина курса (FR-COURSE-02/04/07, FR-COURSE-HYB-01). */
@Service
public class CourseCommandService {

    private static final String FIELD_TITLE = "title";
    private static final String FIELD_DESCRIPTION = "description";
    private static final String FIELD_VISIBILITY = "visibility";

    private final CourseRepository courses;
    private final CourseWriter writer;
    private final AccessService access;
    private final CurrentUserProvider currentUser;
    private final EnrollmentApi enrollment;
    private final CourseContentFiles content;
    private final CourseChangeEvents events;
    private final CourseQueryService queries;
    private final TrashPolicy trash;
    private final Clock clock;

    public CourseCommandService(CourseRepository courses, CourseWriter writer, AccessService access,
                                CurrentUserProvider currentUser, EnrollmentApi enrollment, CourseContentFiles content,
                                CourseChangeEvents events, CourseQueryService queries, AppProperties properties, Clock clock) {
        this.courses = courses;
        this.writer = writer;
        this.access = access;
        this.currentUser = currentUser;
        this.enrollment = enrollment;
        this.content = content;
        this.events = events;
        this.queries = queries;
        this.trash = new TrashPolicy(properties.trash().retention());
        this.clock = clock;
    }

    /** UX-02: достаточно названия; курс создаётся скрытым, автор — преподаватель. */
    @Transactional
    public CourseView create(CreateCourse command) {
        CurrentUser user = currentUser.require();
        access.require(Permission.COURSE_CREATE, categoryContext(command.categoryId()));
        CourseTexts.requireTitle(command.title(), FIELD_TITLE);
        Course course = Course.blank(Ids.newId(), user.tenantId(), user.userId(), clock.instant()).toBuilder()
                .title(command.title()).shortName(command.shortName()).categoryId(command.categoryId())
                .slug(writer.uniqueSlug(user.tenantId(), command.title()))
                .description(sanitizedDescription(user.tenantId(), command.description()))
                .coverFileId(command.coverFileId()).startsAt(command.startsAt()).endsAt(command.endsAt())
                .build();
        writer.insertNew(course, user.userId());
        return queries.get(course.id());
    }

    @Transactional
    public CourseView update(UUID courseId, CoursePatch patch, long expectedVersion) {
        CurrentUser user = currentUser.require();
        Set<Permission> permissions = access.permissions(AccessContext.course(courseId));
        requirePatchPermissions(patch, permissions);
        Course current = courses.find(user.tenantId(), courseId).orElseThrow(CoursesErrors::courseNotFound);
        IfMatch.check(expectedVersion, current.version());
        Course updated = applyPatch(current, patch);
        if (!Objects.equals(current.categoryId(), updated.categoryId())) {
            access.require(Permission.COURSE_CREATE, categoryContext(updated.categoryId()));
        }
        writer.save(current, updated, expectedVersion);
        auditUpdate(user, current, updated, patch);
        events.courseChanged(user.tenantId(), courseId, ChangeKind.UPDATED, user.userId());
        return queries.get(courseId);
    }

    /** PUT /courses/{id}/price — гибрид: платный курс (FR-ENROL-09); null — бесплатный. */
    @Transactional
    public CourseView setPrice(UUID courseId, Money price) {
        CurrentUser user = currentUser.require();
        access.require(Permission.COURSE_PUBLISH, AccessContext.course(courseId));
        Course current = courses.find(user.tenantId(), courseId).orElseThrow(CoursesErrors::courseNotFound);
        Course updated = current.toBuilder().price(price).build();
        writer.save(current, updated, current.version());
        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("amountMinor", price == null ? null : price.amountMinor());
        diff.put("currency", price == null ? null : price.currency());
        events.audit(user.tenantId(), user.userId(), CourseChangeEvents.OBJECT_COURSE, courseId, "price_changed", courseId, diff);
        events.courseChanged(user.tenantId(), courseId, ChangeKind.UPDATED, user.userId());
        return queries.get(courseId);
    }

    /** В корзину (UX-06: без подтверждения, восстановление — POST /restore). */
    @Transactional
    public void delete(UUID courseId) {
        CurrentUser user = currentUser.require();
        access.require(Permission.COURSE_DELETE, AccessContext.course(courseId));
        courses.find(user.tenantId(), courseId).orElseThrow(CoursesErrors::courseNotFound);
        courses.softDelete(user.tenantId(), courseId, clock.instant());
        events.audit(user.tenantId(), user.userId(), CourseChangeEvents.OBJECT_COURSE, courseId, "deleted", courseId, null);
        events.courseChanged(user.tenantId(), courseId, ChangeKind.DELETED, user.userId());
    }

    /** Восстановление: course.delete на уровне категории/tenant или активный преподаватель курса. */
    @Transactional
    public void restore(UUID courseId) {
        CurrentUser user = currentUser.require();
        Course course = courses.findIncludingDeleted(user.tenantId(), courseId)
                .filter(Course::isDeleted)
                .orElseThrow(CoursesErrors::courseNotFound);
        requireCanRestore(user, course);
        Instant now = clock.instant();
        if (!trash.restorable(course.deletedAt(), now)) {
            throw new BusinessRuleException(CoursesErrors.TRASH_EXPIRED, "Retention period has expired");
        }
        courses.restore(user.tenantId(), courseId, now);
        events.audit(user.tenantId(), user.userId(), CourseChangeEvents.OBJECT_COURSE, courseId, "restored", courseId, null);
        events.courseChanged(user.tenantId(), courseId, ChangeKind.RESTORED, user.userId());
    }

    private void requireCanRestore(CurrentUser user, Course course) {
        boolean manager = access.can(Permission.COURSE_DELETE, categoryContext(course.categoryId()));
        boolean teacher = enrollment.membership(user.tenantId(), course.id(), user.userId())
                .filter(member -> member.role() == CourseRole.TEACHER && CourseQueryService.ACTIVE.equals(member.status()))
                .isPresent();
        if (!manager && !teacher) {
            throw new ForbiddenException(PermissionChecks.ACCESS_DENIED, "Missing permission course.delete",
                    Map.of("permission", Permission.COURSE_DELETE.key()));
        }
    }

    private static void requirePatchPermissions(CoursePatch patch, Set<Permission> permissions) {
        PermissionChecks.require(permissions, Permission.COURSE_EDIT);
        if (patch.touchesPublication()) {
            PermissionChecks.require(permissions, Permission.COURSE_PUBLISH);
        }
        if (patch.selfEnrol().isPresent()) {
            PermissionChecks.require(permissions, Permission.ENROLLMENT_MANAGE);
        }
        if (patch.groupMode().isPresent()) {
            PermissionChecks.require(permissions, Permission.GROUP_MANAGE);
        }
    }

    private Course applyPatch(Course current, CoursePatch patch) {
        if (patch.title().isPresent()) {
            CourseTexts.requireTitle(patch.title().value(), FIELD_TITLE);
        }
        Course.Builder builder = current.toBuilder()
                .title(patch.title().applyTo(current.title()))
                .shortName(patch.shortName().applyTo(current.shortName()))
                .categoryId(patch.categoryId().applyTo(current.categoryId()))
                .coverFileId(patch.coverFileId().applyTo(current.coverFileId()))
                .startsAt(patch.startsAt().applyTo(current.startsAt()))
                .endsAt(patch.endsAt().applyTo(current.endsAt()))
                .publishAt(patch.publishAt().applyTo(current.publishAt()))
                .price(patch.price().applyTo(current.price()));
        if (patch.description().isPresent()) {
            builder.description(sanitizedDescription(current.tenantId(), patch.description().value()));
        }
        applyEnums(builder, patch);
        return builder.build();
    }

    private static void applyEnums(Course.Builder builder, CoursePatch patch) {
        if (patch.visibility().isPresent()) {
            builder.visibility(parseVisibility(patch.visibility().value()));
        }
        if (patch.selfEnrol().isPresent()) {
            builder.selfEnrol(Objects.requireNonNullElse(patch.selfEnrol().value(), SelfEnrolSettings.DISABLED));
        }
        if (patch.completionRule().isPresent()) {
            builder.completionRule(Objects.requireNonNullElse(patch.completionRule().value(), CourseCompletionRule.EMPTY));
        }
        if (patch.groupMode().isPresent()) {
            builder.groupMode(patch.groupMode().value() == null ? GroupMode.NONE : GroupMode.fromKey(patch.groupMode().value()));
        }
    }

    static Visibility parseVisibility(String key) {
        if (key == null) {
            throw ValidationException.single(FIELD_VISIBILITY, "required", "Visibility is required");
        }
        return Visibility.fromKey(key);
    }

    private Map<String, Object> sanitizedDescription(UUID tenantId, Object description) {
        return content.sanitizeDoc(tenantId, description, FIELD_DESCRIPTION).map(SanitizedDoc::doc).orElse(null);
    }

    private void auditUpdate(CurrentUser user, Course before, Course after, CoursePatch patch) {
        events.audit(user.tenantId(), user.userId(), CourseChangeEvents.OBJECT_COURSE, after.id(), "updated", after.id(),
                Map.of("fields", patch.presentFields()));
        if (before.visibility() != after.visibility() || !Objects.equals(before.publishAt(), after.publishAt())) {
            Map<String, Object> diff = new LinkedHashMap<>();
            diff.put("from", before.visibility().key());
            diff.put("to", after.visibility().key());
            diff.put("publishAt", after.publishAt() == null ? null : after.publishAt().toString());
            events.audit(user.tenantId(), user.userId(), CourseChangeEvents.OBJECT_COURSE, after.id(), "visibility_changed",
                    after.id(), diff);
        }
    }

    static AccessContext categoryContext(UUID categoryId) {
        return categoryId == null ? AccessContext.tenant() : AccessContext.category(categoryId);
    }
}
