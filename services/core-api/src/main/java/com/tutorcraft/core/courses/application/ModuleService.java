package com.tutorcraft.core.courses.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.courses.CourseEvents.ChangeKind;
import com.tutorcraft.core.courses.Visibility;
import com.tutorcraft.core.courses.application.CourseCommands.ModulePatch;
import com.tutorcraft.core.courses.application.OutlineView.ModuleView;
import com.tutorcraft.core.courses.domain.Course;
import com.tutorcraft.core.courses.domain.CourseItem;
import com.tutorcraft.core.courses.domain.CourseModule;
import com.tutorcraft.core.courses.domain.CourseTexts;
import com.tutorcraft.core.courses.domain.ItemRules;
import com.tutorcraft.core.courses.domain.ModuleHierarchy;
import com.tutorcraft.core.courses.domain.TrashPolicy;
import com.tutorcraft.core.shared.api.IfMatch;
import com.tutorcraft.core.shared.config.AppProperties;
import com.tutorcraft.core.shared.domain.BusinessRuleException;
import com.tutorcraft.core.shared.domain.ConflictException;
import com.tutorcraft.core.shared.domain.Ids;
import com.tutorcraft.core.shared.domain.ValidationException;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Модули курса: создание, inline-правка, перемещение, корзина (FR-COURSE-03/04/07, UX-04/06). */
@Service
public class ModuleService {

    private static final String FIELD_TITLE = "title";
    private static final String FIELD_PUBLISH_AT = "publishAt";

    private final CourseRepository courses;
    private final ModuleRepository modules;
    private final ItemRepository items;
    private final AccessService access;
    private final CurrentUserProvider currentUser;
    private final CourseChangeEvents events;
    private final LearnerStateResolver structure;
    private final OutlineAssembler outlines;
    private final TrashPolicy trash;
    private final Clock clock;

    public ModuleService(CourseRepository courses, ModuleRepository modules, ItemRepository items, AccessService access,
                         CurrentUserProvider currentUser, CourseChangeEvents events, LearnerStateResolver structure,
                         OutlineAssembler outlines, AppProperties properties, Clock clock) {
        this.courses = courses;
        this.modules = modules;
        this.items = items;
        this.access = access;
        this.currentUser = currentUser;
        this.events = events;
        this.structure = structure;
        this.outlines = outlines;
        this.trash = new TrashPolicy(properties.trash().retention());
        this.clock = clock;
    }

    /** Подмодуль наследует видимость родителя; модуль верхнего уровня создаётся опубликованным. */
    @Transactional
    public ModuleView create(UUID courseId, String title, UUID parentId) {
        CurrentUser user = currentUser.require();
        access.require(Permission.COURSE_EDIT, AccessContext.course(courseId));
        CourseTexts.requireTitle(title, FIELD_TITLE);
        List<CourseModule> all = modules.ofCourse(user.tenantId(), courseId);
        CourseModule parent = parentId == null ? null : findInCourse(all, parentId);
        ModuleHierarchy.requireValidParent(null, parent, false);
        Visibility visibility = parent == null ? Visibility.PUBLISHED : parent.visibility();
        CourseModule module = new CourseModule(Ids.newId(), user.tenantId(), courseId, parentId, title.trim(),
                Siblings.ofModules(all, parentId).size(), visibility, parent == null ? null : parent.publishAt(), null, 0, null);
        modules.insertAll(List.of(module));
        events.audit(user.tenantId(), user.userId(), CourseChangeEvents.OBJECT_MODULE, module.id(), "created", courseId, null);
        events.courseChanged(user.tenantId(), courseId, ChangeKind.UPDATED, user.userId());
        return view(user.tenantId(), courseId, module.id());
    }

    @Transactional
    public ModuleView update(UUID moduleId, ModulePatch patch, long expectedVersion) {
        CurrentUser user = currentUser.require();
        CourseModule current = modules.find(user.tenantId(), moduleId).orElseThrow(CoursesErrors::moduleNotFound);
        access.require(Permission.COURSE_EDIT, AccessContext.course(current.courseId()));
        IfMatch.check(expectedVersion, current.version());
        if (patch.title().isPresent()) {
            CourseTexts.requireTitle(patch.title().value(), FIELD_TITLE);
        }
        Visibility visibility = patch.visibility().isPresent()
                ? CourseCommandService.parseVisibility(patch.visibility().value()) : current.visibility();
        Instant publishAt = patch.publishAt().applyTo(current.publishAt());
        requireScheduleDate(visibility, publishAt);
        Map<String, Object> conditions = patch.conditions().isPresent()
                ? ItemRules.conditions(patch.conditions().value(), "conditions") : current.conditions();
        CourseModule updated = current.withContent(patch.title().applyTo(current.title()).trim(), visibility, publishAt, conditions);
        if (!modules.update(updated, expectedVersion)) {
            throw new ConflictException(IfMatch.VERSION_CONFLICT_CODE, "Module was modified by someone else");
        }
        auditVisibility(user, current, updated);
        events.audit(user.tenantId(), user.userId(), CourseChangeEvents.OBJECT_MODULE, moduleId, "updated", current.courseId(), null);
        events.courseChanged(user.tenantId(), current.courseId(), ChangeKind.UPDATED, user.userId());
        return view(user.tenantId(), current.courseId(), moduleId);
    }

    /** Перемещение (drag&drop): позиция среди соседей нового родителя; parentId == null — верхний уровень. */
    @Transactional
    public void move(UUID moduleId, int position, UUID parentId) {
        CurrentUser user = currentUser.require();
        CourseModule module = modules.find(user.tenantId(), moduleId).orElseThrow(CoursesErrors::moduleNotFound);
        access.require(Permission.COURSE_EDIT, AccessContext.course(module.courseId()));
        List<CourseModule> all = modules.ofCourse(user.tenantId(), module.courseId());
        CourseModule parent = parentId == null ? null : findInCourse(all, parentId);
        boolean hasChildren = all.stream().anyMatch(other -> moduleId.equals(other.parentId()));
        ModuleHierarchy.requireValidParent(moduleId, parent, hasChildren);
        if (!Objects.equals(module.parentId(), parentId)) {
            Siblings oldSiblings = Siblings.ofModules(all, module.parentId());
            modules.updatePositions(user.tenantId(), oldSiblings.changes(oldSiblings.without(moduleId), moduleId));
        }
        Siblings target = Siblings.ofModules(all, parentId);
        List<UUID> order = target.withInserted(moduleId, position);
        modules.updatePositions(user.tenantId(), target.changes(order, moduleId));
        modules.move(user.tenantId(), moduleId, parentId, order.indexOf(moduleId));
        events.audit(user.tenantId(), user.userId(), CourseChangeEvents.OBJECT_MODULE, moduleId, "moved", module.courseId(), null);
        events.courseChanged(user.tenantId(), module.courseId(), ChangeKind.UPDATED, user.userId());
    }

    /** В корзину вместе с подмодулями и элементами (одна метка времени — восстановление каскадом). */
    @Transactional
    public void delete(UUID moduleId) {
        CurrentUser user = currentUser.require();
        CourseModule module = modules.find(user.tenantId(), moduleId).orElseThrow(CoursesErrors::moduleNotFound);
        access.require(Permission.COURSE_EDIT, AccessContext.course(module.courseId()));
        List<CourseModule> all = modules.ofCourse(user.tenantId(), module.courseId());
        List<UUID> moduleIds = Stream.concat(Stream.of(moduleId),
                all.stream().filter(other -> moduleId.equals(other.parentId())).map(CourseModule::id)).toList();
        List<CourseItem> affected = items.ofModules(user.tenantId(), moduleIds);
        Instant now = clock.instant();
        modules.softDelete(user.tenantId(), moduleIds, now);
        items.softDelete(user.tenantId(), affected.stream().map(CourseItem::id).toList(), now);
        Siblings siblings = Siblings.ofModules(all, module.parentId());
        modules.updatePositions(user.tenantId(), siblings.changes(siblings.without(moduleId), moduleId));
        affected.forEach(item -> events.itemChanged(item, ChangeKind.DELETED, user.userId()));
        events.audit(user.tenantId(), user.userId(), CourseChangeEvents.OBJECT_MODULE, moduleId, "deleted", module.courseId(), null);
        events.courseChanged(user.tenantId(), module.courseId(), ChangeKind.UPDATED, user.userId());
    }

    /** Восстановление модуля и всего, что удалено вместе с ним; модуль встаёт в конец соседей. */
    @Transactional
    public void restore(UUID moduleId) {
        CurrentUser user = currentUser.require();
        CourseModule module = modules.findIncludingDeleted(user.tenantId(), moduleId).orElseThrow(CoursesErrors::moduleNotFound);
        access.require(Permission.COURSE_EDIT, AccessContext.course(module.courseId()));
        if (!module.isDeleted()) {
            return;
        }
        requireRestorable(module.deletedAt());
        List<CourseModule> live = modules.ofCourse(user.tenantId(), module.courseId());
        if (module.parentId() != null && live.stream().noneMatch(other -> other.id().equals(module.parentId()))) {
            throw new BusinessRuleException(CoursesErrors.MODULE_PARENT_DELETED, "Restore the parent module first");
        }
        List<UUID> moduleIds = cascadeModules(user.tenantId(), module);
        List<CourseItem> cascadeItems = items.deletedSince(user.tenantId(), module.courseId(), module.deletedAt()).stream()
                .filter(item -> moduleIds.contains(item.moduleId()) && module.deletedAt().equals(item.deletedAt()))
                .toList();
        modules.restore(user.tenantId(), moduleIds);
        items.restore(user.tenantId(), cascadeItems.stream().map(CourseItem::id).toList(), clock.instant());
        modules.move(user.tenantId(), moduleId, module.parentId(), Siblings.ofModules(live, module.parentId()).size());
        cascadeItems.forEach(item -> events.itemChanged(item, ChangeKind.RESTORED, user.userId()));
        events.audit(user.tenantId(), user.userId(), CourseChangeEvents.OBJECT_MODULE, moduleId, "restored", module.courseId(), null);
        events.courseChanged(user.tenantId(), module.courseId(), ChangeKind.UPDATED, user.userId());
    }

    private List<UUID> cascadeModules(UUID tenantId, CourseModule module) {
        return Stream.concat(Stream.of(module.id()), modules.deletedSince(tenantId, module.courseId(), module.deletedAt()).stream()
                .filter(child -> module.id().equals(child.parentId()) && module.deletedAt().equals(child.deletedAt()))
                .map(CourseModule::id)).toList();
    }

    private void requireRestorable(Instant deletedAt) {
        if (!trash.restorable(deletedAt, clock.instant())) {
            throw new BusinessRuleException(CoursesErrors.TRASH_EXPIRED, "Retention period has expired");
        }
    }

    private static CourseModule findInCourse(List<CourseModule> all, UUID moduleId) {
        return all.stream().filter(module -> module.id().equals(moduleId)).findFirst()
                .orElseThrow(CoursesErrors::moduleNotFound);
    }

    private static void requireScheduleDate(Visibility visibility, Instant publishAt) {
        if (visibility == Visibility.SCHEDULED && publishAt == null) {
            throw ValidationException.single(FIELD_PUBLISH_AT, "required", "Publish date is required for scheduled visibility");
        }
    }

    private void auditVisibility(CurrentUser user, CourseModule before, CourseModule after) {
        if (before.visibility() != after.visibility()) {
            events.audit(user.tenantId(), user.userId(), CourseChangeEvents.OBJECT_MODULE, after.id(), "visibility_changed",
                    after.courseId(), Map.of("from", before.visibility().key(), "to", after.visibility().key()));
        }
    }

    private ModuleView view(UUID tenantId, UUID courseId, UUID moduleId) {
        Course course = courses.find(tenantId, courseId).orElseThrow(CoursesErrors::courseNotFound);
        return outlines.module(structure.forStaff(course), moduleId).orElseThrow(CoursesErrors::moduleNotFound);
    }
}
