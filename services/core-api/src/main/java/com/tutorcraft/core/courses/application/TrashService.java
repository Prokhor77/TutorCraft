package com.tutorcraft.core.courses.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.courses.application.CourseRepository.TenantCourseId;
import com.tutorcraft.core.courses.domain.Course;
import com.tutorcraft.core.courses.domain.CourseItem;
import com.tutorcraft.core.courses.domain.CourseModule;
import com.tutorcraft.core.courses.domain.TrashPolicy;
import com.tutorcraft.core.shared.config.AppProperties;
import com.tutorcraft.core.shared.domain.NotFoundException;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Корзина (FR-COURSE-07, UX-06): список восстанавливаемого и ежедневная очистка просроченного.
 * В списке — только «корни» удаления: элементы/подмодули, удалённые вместе с модулем, восстанавливаются вместе с ним.
 */
@Service
public class TrashService {

    private static final Logger log = LoggerFactory.getLogger(TrashService.class);

    private final CourseRepository courses;
    private final ModuleRepository modules;
    private final ItemRepository items;
    private final AccessService access;
    private final CurrentUserProvider currentUser;
    private final CourseScopes scopes;
    private final TrashPolicy trash;
    private final Clock clock;

    public TrashService(CourseRepository courses, ModuleRepository modules, ItemRepository items, AccessService access,
                        CurrentUserProvider currentUser, CourseScopes scopes, AppProperties properties, Clock clock) {
        this.courses = courses;
        this.modules = modules;
        this.items = items;
        this.access = access;
        this.currentUser = currentUser;
        this.scopes = scopes;
        this.trash = new TrashPolicy(properties.trash().retention());
        this.clock = clock;
    }

    /** courseId != null — удалённые модули/элементы курса; иначе — удалённые курсы, доступные для восстановления. */
    @Transactional(noRollbackFor = NotFoundException.class)
    public List<TrashEntryView> list(UUID courseId) {
        CurrentUser user = currentUser.require();
        Instant since = trash.cutoff(clock.instant());
        List<TrashEntryView> entries = courseId == null ? deletedCourses(user, since) : deletedContent(user, courseId, since);
        return entries.stream().sorted(Comparator.comparing(TrashEntryView::deletedAt).reversed()).toList();
    }

    private List<TrashEntryView> deletedCourses(CurrentUser user, Instant since) {
        return courses.deletedSince(user.tenantId(), scopes.restorable(user), since).stream()
                .map(this::courseEntry)
                .toList();
    }

    private List<TrashEntryView> deletedContent(CurrentUser user, UUID courseId, Instant since) {
        access.require(Permission.COURSE_EDIT, AccessContext.course(courseId));
        List<CourseModule> deletedModules = modules.deletedSince(user.tenantId(), courseId, since);
        Set<String> moduleCascades = deletedModules.stream()
                .map(module -> cascadeKey(module.id(), module.deletedAt()))
                .collect(Collectors.toSet());
        Stream<TrashEntryView> moduleEntries = deletedModules.stream()
                .filter(module -> module.isTopLevel() || !moduleCascades.contains(cascadeKey(module.parentId(), module.deletedAt())))
                .map(this::moduleEntry);
        Stream<TrashEntryView> itemEntries = items.deletedSince(user.tenantId(), courseId, since).stream()
                .filter(item -> !moduleCascades.contains(cascadeKey(item.moduleId(), item.deletedAt())))
                .map(this::itemEntry);
        return Stream.concat(moduleEntries, itemEntries).toList();
    }

    /**
     * Системная очистка просроченного (все tenant): элементы и модули MongoDB удаляются физически; курс PostgreSQL —
     * только если на него не ссылаются данные других модулей (иначе остаётся в корзине навсегда).
     */
    public void purgeExpired() {
        Instant cutoff = trash.cutoff(clock.instant());
        long purgedItems = items.purgeDeletedBefore(cutoff);
        long purgedModules = modules.purgeDeletedBefore(cutoff);
        int purgedCourses = 0;
        for (TenantCourseId course : courses.deletedBefore(cutoff)) {
            if (courses.hardDelete(course.tenantId(), course.courseId())) {
                items.deleteAllOfCourse(course.tenantId(), course.courseId());
                modules.deleteAllOfCourse(course.tenantId(), course.courseId());
                purgedCourses++;
            }
        }
        log.info("Trash purge: {} items, {} modules, {} courses removed", purgedItems, purgedModules, purgedCourses);
    }

    private static String cascadeKey(UUID parentId, Instant deletedAt) {
        return parentId + "@" + deletedAt;
    }

    private TrashEntryView courseEntry(Course course) {
        return new TrashEntryView(TrashEntryView.KIND_COURSE, course.id(), course.id(), course.title(), null, course.deletedAt(),
                trash.purgeAt(course.deletedAt()));
    }

    private TrashEntryView moduleEntry(CourseModule module) {
        return new TrashEntryView(TrashEntryView.KIND_MODULE, module.id(), module.courseId(), module.title(), null,
                module.deletedAt(), trash.purgeAt(module.deletedAt()));
    }

    private TrashEntryView itemEntry(CourseItem item) {
        return new TrashEntryView(TrashEntryView.KIND_ITEM, item.id(), item.courseId(), item.title(), item.type().key(),
                item.deletedAt(), trash.purgeAt(item.deletedAt()));
    }
}
