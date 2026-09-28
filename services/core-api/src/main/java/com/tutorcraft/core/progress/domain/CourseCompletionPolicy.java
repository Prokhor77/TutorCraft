package com.tutorcraft.core.progress.domain;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Завершение курса (FR-PROG-05): все обязательные элементы выполнены и/или итоговая оценка не ниже порога.
 * Без правила (нет обязательных элементов и порога) курс не завершается автоматически.
 */
public final class CourseCompletionPolicy {

    private CourseCompletionPolicy() {
    }

    public static boolean hasRule(Collection<UUID> requiredItemIds, Double minFinalPercent) {
        return !requiredItemIds.isEmpty() || minFinalPercent != null;
    }

    public static boolean isComplete(Collection<UUID> requiredItemIds, Double minFinalPercent, Set<UUID> completedItems,
                                     Double finalPercent) {
        if (!hasRule(requiredItemIds, minFinalPercent)) {
            return false;
        }
        boolean itemsDone = completedItems.containsAll(requiredItemIds);
        boolean gradeReached = minFinalPercent == null || (finalPercent != null && finalPercent >= minFinalPercent);
        return itemsDone && gradeReached;
    }
}
