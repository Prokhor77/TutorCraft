package com.tutorcraft.core.courses.application;

import com.tutorcraft.core.courses.CourseEvents.ChangeKind;
import com.tutorcraft.core.courses.domain.CourseItem;
import com.tutorcraft.core.courses.domain.CourseModule;
import com.tutorcraft.core.courses.domain.StructuredValues;
import com.tutorcraft.core.shared.domain.Ids;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Глубокое копирование модулей и элементов (FR-COURSE-06): новые id, переназначение ссылок внутри копируемого набора,
 * связывание файлов с новыми элементами, событие ItemChanged(CREATED) по каждому элементу.
 */
@Component
class StructureCopier {

    private final ModuleRepository modules;
    private final ItemRepository items;
    private final ActivityTypeRegistry types;
    private final CourseContentFiles files;
    private final CourseChangeEvents events;
    private final Clock clock;

    StructureCopier(ModuleRepository modules, ItemRepository items, ActivityTypeRegistry types, CourseContentFiles files,
                    CourseChangeEvents events, Clock clock) {
        this.modules = modules;
        this.items = items;
        this.types = types;
        this.files = files;
        this.events = events;
        this.clock = clock;
    }

    /** Результат копирования: новые модули/элементы и отображение старых id на новые. */
    record Copy(List<CourseModule> modules, List<CourseItem> items, Map<UUID, UUID> mapping) {

        Copy replaceModule(CourseModule replacement) {
            return new Copy(modules.stream().map(m -> m.id().equals(replacement.id()) ? replacement : m).toList(), items, mapping);
        }

        Copy replaceItem(CourseItem replacement) {
            return new Copy(modules, items.stream().map(i -> i.id().equals(replacement.id()) ? replacement : i).toList(), mapping);
        }

        CourseModule module(UUID id) {
            return modules.stream().filter(m -> m.id().equals(id)).findFirst().orElseThrow();
        }

        CourseItem item(UUID id) {
            return items.stream().filter(i -> i.id().equals(id)).findFirst().orElseThrow();
        }
    }

    /**
     * Готовит копии (без записи). Родители/модули вне копируемого набора сохраняются как есть,
     * поэтому копию модуля можно положить рядом с оригиналом, а копию курса — в новый курс.
     */
    Copy prepare(UUID targetCourseId, List<CourseModule> sourceModules, List<CourseItem> sourceItems) {
        Map<UUID, UUID> mapping = new HashMap<>();
        sourceModules.forEach(module -> mapping.put(module.id(), Ids.newId()));
        sourceItems.forEach(item -> mapping.put(item.id(), Ids.newId()));
        Instant now = clock.instant();
        List<CourseModule> moduleCopies = sourceModules.stream()
                .map(module -> module.copy(mapping.get(module.id()), targetCourseId, remapped(module.parentId(), mapping),
                        module.title(), module.position(), module.visibility(),
                        StructuredValues.remapMap(module.conditions(), mapping)))
                .toList();
        List<CourseItem> itemCopies = sourceItems.stream()
                .map(item -> item.copy(mapping.get(item.id()), targetCourseId, remapped(item.moduleId(), mapping), item.title(),
                        item.position(), mapping, now))
                .toList();
        return new Copy(moduleCopies, itemCopies, Map.copyOf(mapping));
    }

    /** Записывает подготовленную копию. */
    void persist(Copy copy, UUID actorId) {
        if (!copy.modules().isEmpty()) {
            modules.insertAll(copy.modules());
        }
        if (!copy.items().isEmpty()) {
            items.insertAll(copy.items());
        }
        copy.items().forEach(item -> {
            linkItemFiles(item);
            events.itemChanged(item, ChangeKind.CREATED, actorId);
        });
    }

    /** Файлы настроек и контента элемента связываются с элементом (FileLink 'item'). */
    void linkItemFiles(CourseItem item) {
        Set<UUID> fileIds = new HashSet<>(types.referencedFileIds(item.type(), item.settings()));
        fileIds.addAll(CourseContentFiles.docFileIds(item.content()));
        files.link(item.tenantId(), fileIds, CourseContentFiles.OWNER_ITEM, item.id());
    }

    private static UUID remapped(UUID id, Map<UUID, UUID> mapping) {
        return id == null ? null : mapping.getOrDefault(id, id);
    }
}
