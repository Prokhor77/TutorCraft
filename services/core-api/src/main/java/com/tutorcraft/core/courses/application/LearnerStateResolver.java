package com.tutorcraft.core.courses.application;

import com.tutorcraft.core.courses.ItemRef;
import com.tutorcraft.core.courses.ModuleRef;
import com.tutorcraft.core.courses.domain.Course;
import com.tutorcraft.core.courses.domain.CourseItem;
import com.tutorcraft.core.courses.domain.CourseModule;
import com.tutorcraft.core.courses.domain.LearnerVisibility;
import com.tutorcraft.core.courses.spi.ItemStatusProvider;
import com.tutorcraft.core.courses.spi.LearnerStateProvider;
import com.tutorcraft.core.courses.spi.LearnerStateProvider.LearnerState;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Собирает снимок курса для учащегося: видимость (FR-COURSE-04), доступность и выполнение (LearnerStateProvider,
 * модуль progress; при его отсутствии всё доступно), статусы активностей (ItemStatusProvider).
 */
@Component
class LearnerStateResolver {

    private final ModuleRepository modules;
    private final ItemRepository items;
    private final ObjectProvider<LearnerStateProvider> stateProvider;
    private final ObjectProvider<ItemStatusProvider> statusProviders;
    private final Clock clock;

    LearnerStateResolver(ModuleRepository modules, ItemRepository items, ObjectProvider<LearnerStateProvider> stateProvider,
                         ObjectProvider<ItemStatusProvider> statusProviders, Clock clock) {
        this.modules = modules;
        this.items = items;
        this.stateProvider = stateProvider;
        this.statusProviders = statusProviders;
        this.clock = clock;
    }

    LearnerSnapshot forStaff(Course course) {
        return LearnerSnapshot.staff(modules.ofCourse(course.tenantId(), course.id()), items.ofCourse(course.tenantId(), course.id()));
    }

    LearnerSnapshot forLearner(Course course, UUID userId) {
        Instant now = clock.instant();
        List<CourseModule> visibleModules = visibleModules(modules.ofCourse(course.tenantId(), course.id()), now);
        Set<UUID> moduleIds = visibleModules.stream().map(CourseModule::id).collect(Collectors.toSet());
        List<CourseItem> visibleItems = items.ofCourse(course.tenantId(), course.id()).stream()
                .filter(item -> moduleIds.contains(item.moduleId()) && item.visibleAt(now))
                .toList();
        LearnerState state = learnerState(course, userId, visibleModules, visibleItems);
        return LearnerSnapshot.learner(visibleModules, visibleItems, state.availability(), state.completion(),
                statuses(course.tenantId(), userId, visibleItems));
    }

    private static List<CourseModule> visibleModules(List<CourseModule> all, Instant now) {
        Map<UUID, CourseModule> byId = all.stream().collect(Collectors.toMap(CourseModule::id, Function.identity()));
        return all.stream()
                .filter(module -> LearnerVisibility.moduleVisible(module,
                        module.isTopLevel() ? null : byId.get(module.parentId()), now))
                .toList();
    }

    private LearnerState learnerState(Course course, UUID userId, List<CourseModule> visibleModules,
                                      List<CourseItem> visibleItems) {
        LearnerStateProvider provider = stateProvider.getIfAvailable();
        if (provider == null) {
            return new LearnerState(Map.of(), Map.of());
        }
        List<ModuleRef> moduleRefs = visibleModules.stream().map(CourseModule::toRef).toList();
        List<ItemRef> itemRefs = visibleItems.stream().map(CourseItem::toRef).toList();
        LearnerState state = provider.stateFor(course.tenantId(), userId, course.toRef(), moduleRefs, itemRefs);
        return state == null ? new LearnerState(Map.of(), Map.of()) : state;
    }

    private Map<UUID, String> statuses(UUID tenantId, UUID userId, List<CourseItem> visibleItems) {
        Map<UUID, String> result = new HashMap<>();
        statusProviders.orderedStream().forEach(provider -> {
            List<ItemRef> supported = visibleItems.stream()
                    .filter(item -> provider.supportedTypes().contains(item.type()))
                    .map(CourseItem::toRef)
                    .toList();
            if (!supported.isEmpty()) {
                result.putAll(provider.statuses(tenantId, userId, supported));
            }
        });
        return result;
    }
}
