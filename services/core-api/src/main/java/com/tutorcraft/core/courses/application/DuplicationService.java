package com.tutorcraft.core.courses.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.courses.CourseEvents.ChangeKind;
import com.tutorcraft.core.courses.Visibility;
import com.tutorcraft.core.courses.application.OutlineView.ModuleView;
import com.tutorcraft.core.courses.application.StructureCopier.Copy;
import com.tutorcraft.core.courses.domain.Course;
import com.tutorcraft.core.courses.domain.CourseCompletionRule;
import com.tutorcraft.core.courses.domain.CourseItem;
import com.tutorcraft.core.courses.domain.CourseModule;
import com.tutorcraft.core.courses.domain.CourseTexts;
import com.tutorcraft.core.courses.domain.StructuredValues;
import com.tutorcraft.core.shared.domain.Ids;
import com.tutorcraft.core.shared.i18n.Messages;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Дублирование элемента, модуля, курса (FR-COURSE-06). Копия элемента/модуля встаёт сразу после оригинала,
 * название получает суффикс «(копия)»; копия курса создаётся скрытой, без краткого имени и самозаписи.
 */
@Service
public class DuplicationService {

    private static final String DUPLICATED = "duplicated";
    private static final String SOURCE_ID = "sourceId";

    private final CourseRepository courses;
    private final ModuleRepository modules;
    private final ItemRepository items;
    private final StructureCopier copier;
    private final CourseWriter writer;
    private final AccessService access;
    private final CurrentUserProvider currentUser;
    private final CourseChangeEvents events;
    private final CourseQueryService courseQueries;
    private final LearnerStateResolver structure;
    private final OutlineAssembler outlines;
    private final ItemViews itemViews;
    private final Messages messages;
    private final Clock clock;

    public DuplicationService(CourseRepository courses, ModuleRepository modules, ItemRepository items, StructureCopier copier,
                              CourseWriter writer, AccessService access, CurrentUserProvider currentUser,
                              CourseChangeEvents events, CourseQueryService courseQueries, LearnerStateResolver structure,
                              OutlineAssembler outlines, ItemViews itemViews, Messages messages, Clock clock) {
        this.courses = courses;
        this.modules = modules;
        this.items = items;
        this.copier = copier;
        this.writer = writer;
        this.access = access;
        this.currentUser = currentUser;
        this.events = events;
        this.courseQueries = courseQueries;
        this.structure = structure;
        this.outlines = outlines;
        this.itemViews = itemViews;
        this.messages = messages;
        this.clock = clock;
    }

    @Transactional
    public CourseView duplicateCourse(UUID courseId) {
        CurrentUser user = currentUser.require();
        access.require(Permission.COURSE_EDIT, AccessContext.course(courseId));
        Course source = courses.find(user.tenantId(), courseId).orElseThrow(CoursesErrors::courseNotFound);
        access.require(Permission.COURSE_CREATE, CourseCommandService.categoryContext(source.categoryId()));
        UUID newId = Ids.newId();
        Copy copy = copier.prepare(newId, modules.ofCourse(user.tenantId(), courseId), items.ofCourse(user.tenantId(), courseId));
        String title = copyTitle(source.title());
        Course course = Course.blank(newId, user.tenantId(), user.userId(), clock.instant()).toBuilder()
                .title(title).slug(writer.uniqueSlug(user.tenantId(), title)).categoryId(source.categoryId())
                .description(StructuredValues.copyMap(source.description())).coverFileId(source.coverFileId())
                .startsAt(source.startsAt()).endsAt(source.endsAt()).visibility(Visibility.HIDDEN).price(source.price())
                .completionRule(remapRule(source.completionRule(), copy.mapping())).groupMode(source.groupMode())
                .build();
        writer.insertNew(course, user.userId());
        copier.persist(copy, user.userId());
        events.audit(user.tenantId(), user.userId(), CourseChangeEvents.OBJECT_COURSE, newId, DUPLICATED, newId,
                Map.of(SOURCE_ID, courseId.toString()));
        return courseQueries.get(newId);
    }

    @Transactional
    public ModuleView duplicateModule(UUID moduleId) {
        CurrentUser user = currentUser.require();
        CourseModule source = modules.find(user.tenantId(), moduleId).orElseThrow(CoursesErrors::moduleNotFound);
        access.require(Permission.COURSE_EDIT, AccessContext.course(source.courseId()));
        List<CourseModule> all = modules.ofCourse(user.tenantId(), source.courseId());
        List<CourseModule> sourceModules = Stream.concat(Stream.of(source),
                all.stream().filter(module -> moduleId.equals(module.parentId()))).toList();
        Copy copy = copier.prepare(source.courseId(), sourceModules,
                items.ofModules(user.tenantId(), sourceModules.stream().map(CourseModule::id).toList()));
        UUID copyId = copy.mapping().get(moduleId);
        Siblings siblings = Siblings.ofModules(all, source.parentId());
        List<UUID> order = siblings.withInserted(copyId, source.position() + 1);
        CourseModule root = copy.module(copyId).withTitle(copyTitle(source.title()))
                .withPlacement(source.parentId(), order.indexOf(copyId));
        copier.persist(copy.replaceModule(root), user.userId());
        modules.updatePositions(user.tenantId(), siblings.changes(order, copyId));
        events.audit(user.tenantId(), user.userId(), CourseChangeEvents.OBJECT_MODULE, copyId, DUPLICATED, source.courseId(),
                Map.of(SOURCE_ID, moduleId.toString()));
        events.courseChanged(user.tenantId(), source.courseId(), ChangeKind.UPDATED, user.userId());
        Course course = courses.find(user.tenantId(), source.courseId()).orElseThrow(CoursesErrors::courseNotFound);
        return outlines.module(structure.forStaff(course), copyId).orElseThrow(CoursesErrors::moduleNotFound);
    }

    @Transactional
    public ItemView duplicateItem(UUID itemId) {
        CurrentUser user = currentUser.require();
        CourseItem source = items.find(user.tenantId(), itemId).orElseThrow(CoursesErrors::itemNotFound);
        access.require(Permission.COURSE_EDIT, AccessContext.course(source.courseId()));
        Copy copy = copier.prepare(source.courseId(), List.of(), List.of(source));
        UUID copyId = copy.mapping().get(itemId);
        Siblings siblings = Siblings.ofItems(items.ofModules(user.tenantId(), List.of(source.moduleId())));
        List<UUID> order = siblings.withInserted(copyId, source.position() + 1);
        CourseItem placed = copy.item(copyId).withTitle(copyTitle(source.title()))
                .withPlacement(source.moduleId(), order.indexOf(copyId));
        copier.persist(copy.replaceItem(placed), user.userId());
        items.updatePositions(user.tenantId(), siblings.changes(order, copyId));
        events.audit(user.tenantId(), user.userId(), CourseChangeEvents.OBJECT_ITEM, copyId, DUPLICATED, source.courseId(),
                Map.of(SOURCE_ID, itemId.toString()));
        return itemViews.staff(placed, null);
    }

    private String copyTitle(String title) {
        return CourseTexts.copyTitle(title, messages.get(CoursesErrors.COPY_SUFFIX));
    }

    private static CourseCompletionRule remapRule(CourseCompletionRule rule, Map<UUID, UUID> mapping) {
        List<UUID> required = rule.requiredItemIds().stream().filter(mapping::containsKey).map(mapping::get).toList();
        return new CourseCompletionRule(required, rule.minFinalPercent());
    }
}
