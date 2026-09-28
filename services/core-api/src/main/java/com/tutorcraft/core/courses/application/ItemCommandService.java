package com.tutorcraft.core.courses.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.courses.CourseEvents.ChangeKind;
import com.tutorcraft.core.courses.ItemType;
import com.tutorcraft.core.courses.Visibility;
import com.tutorcraft.core.courses.application.CourseCommands.CreateItem;
import com.tutorcraft.core.courses.application.CourseCommands.ItemPatch;
import com.tutorcraft.core.courses.domain.CourseItem;
import com.tutorcraft.core.courses.domain.CourseItem.ItemContent;
import com.tutorcraft.core.courses.domain.CourseModule;
import com.tutorcraft.core.courses.domain.CourseTexts;
import com.tutorcraft.core.courses.domain.ItemRules;
import com.tutorcraft.core.courses.domain.TrashPolicy;
import com.tutorcraft.core.shared.api.IfMatch;
import com.tutorcraft.core.shared.config.AppProperties;
import com.tutorcraft.core.shared.content.BlockDocs.SanitizedDoc;
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
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Элементы курса: создание по одному названию (UX-02, AC-2), inline-правка с версией (UX-04), перемещение,
 * корзина (UX-06). Настройки валидирует ActivityType (DATA-04), контент — санитайзер блочных документов (NFR-SEC-03).
 */
@Service
public class ItemCommandService {

    private static final String FIELD_TITLE = "title";
    private static final String FIELD_SETTINGS = "settings";
    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_PUBLISH_AT = "publishAt";

    private final ModuleRepository modules;
    private final ItemRepository items;
    private final ActivityTypeRegistry types;
    private final CourseContentFiles content;
    private final StructureCopier copier;
    private final AccessService access;
    private final CurrentUserProvider currentUser;
    private final CourseChangeEvents events;
    private final ItemViews views;
    private final ConditionRules conditionRules;
    private final TrashPolicy trash;
    private final Clock clock;

    public ItemCommandService(ModuleRepository modules, ItemRepository items, ActivityTypeRegistry types,
                              CourseContentFiles content, StructureCopier copier, AccessService access,
                              CurrentUserProvider currentUser, CourseChangeEvents events, ItemViews views,
                              ConditionRules conditionRules, AppProperties properties, Clock clock) {
        this.modules = modules;
        this.items = items;
        this.types = types;
        this.content = content;
        this.copier = copier;
        this.access = access;
        this.currentUser = currentUser;
        this.events = events;
        this.views = views;
        this.conditionRules = conditionRules;
        this.trash = new TrashPolicy(properties.trash().retention());
        this.clock = clock;
    }

    /** AC-2: достаточно типа и названия; настройки — умолчания типа, видимость — как у модуля. */
    @Transactional
    public ItemView create(UUID moduleId, CreateItem command) {
        CurrentUser user = currentUser.require();
        CourseModule module = modules.find(user.tenantId(), moduleId).orElseThrow(CoursesErrors::moduleNotFound);
        access.require(Permission.COURSE_EDIT, AccessContext.course(module.courseId()));
        ItemType type = ItemType.fromKey(command.type());
        CourseTexts.requireTitle(command.title(), FIELD_TITLE);
        Map<String, Object> settings = types.initialSettings(type, command.settings());
        content.requireReady(user.tenantId(), types.referencedFileIds(type, settings), FIELD_SETTINGS);
        Instant now = clock.instant();
        int position = items.ofModules(user.tenantId(), List.of(moduleId)).size();
        CourseItem item = new CourseItem(Ids.newId(), user.tenantId(), module.courseId(), moduleId, type, command.title().trim(),
                position, module.visibility(), module.publishAt(), settings, null, ItemRules.DEFAULT_COMPLETION_RULE, null,
                types.keyDates(type, settings), 0, null, now, now);
        items.insertAll(List.of(item));
        copier.linkItemFiles(item);
        events.audit(user.tenantId(), user.userId(), CourseChangeEvents.OBJECT_ITEM, item.id(), "created", item.courseId(),
                Map.of("type", type.key()));
        events.itemChanged(item, ChangeKind.CREATED, user.userId());
        return views.staff(item, null);
    }

    @Transactional
    public ItemView update(UUID itemId, ItemPatch patch, long expectedVersion) {
        CurrentUser user = currentUser.require();
        CourseItem current = items.find(user.tenantId(), itemId).orElseThrow(CoursesErrors::itemNotFound);
        access.require(Permission.COURSE_EDIT, AccessContext.course(current.courseId()));
        IfMatch.check(expectedVersion, current.version());
        CourseItem updated = current.withContent(applyPatch(current, patch));
        if (!items.update(updated, expectedVersion, clock.instant())) {
            throw new ConflictException(IfMatch.VERSION_CONFLICT_CODE, "Item was modified by someone else");
        }
        copier.linkItemFiles(updated);
        auditUpdate(user, current, updated, patch);
        events.itemChanged(updated, ChangeKind.UPDATED, user.userId());
        CourseItem saved = items.find(user.tenantId(), itemId).orElseThrow(CoursesErrors::itemNotFound);
        return views.staff(saved, null);
    }

    /** Перемещение в модуль того же курса на позицию (drag&drop, FR-COURSE-03). */
    @Transactional
    public void move(UUID itemId, UUID targetModuleId, int position) {
        CurrentUser user = currentUser.require();
        CourseItem item = items.find(user.tenantId(), itemId).orElseThrow(CoursesErrors::itemNotFound);
        access.require(Permission.COURSE_EDIT, AccessContext.course(item.courseId()));
        CourseModule target = modules.find(user.tenantId(), targetModuleId)
                .filter(module -> module.courseId().equals(item.courseId()))
                .orElseThrow(CoursesErrors::moduleNotFound);
        if (!target.id().equals(item.moduleId())) {
            Siblings source = Siblings.ofItems(items.ofModules(user.tenantId(), List.of(item.moduleId())));
            items.updatePositions(user.tenantId(), source.changes(source.without(itemId), itemId));
        }
        Siblings siblings = Siblings.ofItems(items.ofModules(user.tenantId(), List.of(target.id())));
        List<UUID> order = siblings.withInserted(itemId, position);
        items.updatePositions(user.tenantId(), siblings.changes(order, itemId));
        items.move(user.tenantId(), itemId, target.id(), order.indexOf(itemId), clock.instant());
        events.audit(user.tenantId(), user.userId(), CourseChangeEvents.OBJECT_ITEM, itemId, "moved", item.courseId(),
                Map.of("moduleId", target.id().toString()));
        events.itemChanged(item.withPlacement(target.id(), order.indexOf(itemId)), ChangeKind.UPDATED, user.userId());
    }

    @Transactional
    public void delete(UUID itemId) {
        CurrentUser user = currentUser.require();
        CourseItem item = items.find(user.tenantId(), itemId).orElseThrow(CoursesErrors::itemNotFound);
        access.require(Permission.COURSE_EDIT, AccessContext.course(item.courseId()));
        items.softDelete(user.tenantId(), List.of(itemId), clock.instant());
        Siblings siblings = Siblings.ofItems(items.ofModules(user.tenantId(), List.of(item.moduleId())));
        items.updatePositions(user.tenantId(), siblings.changes(siblings.without(itemId), itemId));
        events.audit(user.tenantId(), user.userId(), CourseChangeEvents.OBJECT_ITEM, itemId, "deleted", item.courseId(), null);
        events.itemChanged(item, ChangeKind.DELETED, user.userId());
    }

    /** Восстановление в конец своего модуля; удалённый модуль нужно восстановить первым. */
    @Transactional
    public void restore(UUID itemId) {
        CurrentUser user = currentUser.require();
        CourseItem item = items.findIncludingDeleted(user.tenantId(), itemId).orElseThrow(CoursesErrors::itemNotFound);
        access.require(Permission.COURSE_EDIT, AccessContext.course(item.courseId()));
        if (!item.isDeleted()) {
            return;
        }
        if (!trash.restorable(item.deletedAt(), clock.instant())) {
            throw new BusinessRuleException(CoursesErrors.TRASH_EXPIRED, "Retention period has expired");
        }
        if (modules.find(user.tenantId(), item.moduleId()).isEmpty()) {
            throw new BusinessRuleException(CoursesErrors.ITEM_MODULE_DELETED, "Restore the module first");
        }
        int position = items.ofModules(user.tenantId(), List.of(item.moduleId())).size();
        items.restore(user.tenantId(), List.of(itemId), clock.instant());
        items.move(user.tenantId(), itemId, item.moduleId(), position, clock.instant());
        events.audit(user.tenantId(), user.userId(), CourseChangeEvents.OBJECT_ITEM, itemId, "restored", item.courseId(), null);
        events.itemChanged(item, ChangeKind.RESTORED, user.userId());
    }

    private ItemContent applyPatch(CourseItem current, ItemPatch patch) {
        ItemContent before = current.editableContent();
        if (patch.title().isPresent()) {
            CourseTexts.requireTitle(patch.title().value(), FIELD_TITLE);
        }
        Visibility visibility = patch.visibility().isPresent()
                ? CourseCommandService.parseVisibility(patch.visibility().value()) : before.visibility();
        Instant publishAt = patch.publishAt().applyTo(before.publishAt());
        if (visibility == Visibility.SCHEDULED && publishAt == null) {
            throw ValidationException.single(FIELD_PUBLISH_AT, "required", "Publish date is required for scheduled visibility");
        }
        Map<String, Object> settings = patch.settings().isPresent() ? patchedSettings(current, patch) : before.settings();
        return new ItemContent(patch.title().applyTo(before.title()).trim(), visibility, publishAt, settings,
                patchedContent(current, patch), patchedCompletionRule(before, patch), patchedConditions(current, before, patch),
                patch.settings().isPresent() ? types.keyDates(current.type(), settings) : before.dates());
    }

    private Map<String, Object> patchedSettings(CourseItem current, ItemPatch patch) {
        Map<String, Object> settings = types.mergedSettings(current.type(), current.settings(), patch.settings().value());
        content.requireReady(current.tenantId(), types.referencedFileIds(current.type(), settings), FIELD_SETTINGS);
        return settings;
    }

    private Map<String, Object> patchedContent(CourseItem current, ItemPatch patch) {
        if (!patch.content().isPresent()) {
            return current.content();
        }
        return content.sanitizeDoc(current.tenantId(), patch.content().value(), FIELD_CONTENT).map(SanitizedDoc::doc).orElse(null);
    }

    private static Map<String, Object> patchedCompletionRule(ItemContent before, ItemPatch patch) {
        return patch.completionRule().isPresent()
                ? ItemRules.completionRule(patch.completionRule().value(), "completionRule") : before.completionRule();
    }

    private Map<String, Object> patchedConditions(CourseItem current, ItemContent before, ItemPatch patch) {
        return patch.conditions().isPresent() ? conditionRules.forItem(current, patch.conditions().value()) : before.conditions();
    }

    private void auditUpdate(CurrentUser user, CourseItem before, CourseItem after, ItemPatch patch) {
        events.audit(user.tenantId(), user.userId(), CourseChangeEvents.OBJECT_ITEM, after.id(), "updated", after.courseId(),
                Map.of("fields", patch.presentFields()));
        if (before.visibility() != after.visibility()) {
            events.audit(user.tenantId(), user.userId(), CourseChangeEvents.OBJECT_ITEM, after.id(), "visibility_changed",
                    after.courseId(), Map.of("from", before.visibility().key(), "to", after.visibility().key()));
        }
    }
}
