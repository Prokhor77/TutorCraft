package com.tutorcraft.core.assessment.assignment.domain;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Действующие сроки студента = настройки задания ⊕ продление (FR-ASSIGN-03).
 * Приоритет: личное продление, затем самое позднее из продлений его групп.
 * Без явного closeAt продления жёсткое закрытие сдвигается не раньше продлённого срока.
 */
public record EffectiveDeadlines(Instant openAt, Instant dueAt, Instant closeAt) {

    public static EffectiveDeadlines of(AssignmentSettings settings, List<ItemOverride> applicable) {
        Optional<ItemOverride> override = choose(applicable);
        if (override.isEmpty()) {
            return new EffectiveDeadlines(settings.openAt(), settings.dueAt(), settings.closeAt());
        }
        ItemOverride extension = override.get();
        return new EffectiveDeadlines(settings.openAt(), extension.dueAt(), closeAt(settings.closeAt(), extension));
    }

    private static Optional<ItemOverride> choose(List<ItemOverride> applicable) {
        List<ItemOverride> extensions = applicable.stream().filter(override -> override.dueAt() != null).toList();
        Optional<ItemOverride> personal = extensions.stream().filter(ItemOverride::forUser).findFirst();
        if (personal.isPresent()) {
            return personal;
        }
        return extensions.stream().max(Comparator.comparing(ItemOverride::dueAt));
    }

    private static Instant closeAt(Instant settingsCloseAt, ItemOverride extension) {
        if (extension.closeAt() != null) {
            return extension.closeAt();
        }
        if (settingsCloseAt == null) {
            return null;
        }
        return settingsCloseAt.isBefore(extension.dueAt()) ? extension.dueAt() : settingsCloseAt;
    }
}
