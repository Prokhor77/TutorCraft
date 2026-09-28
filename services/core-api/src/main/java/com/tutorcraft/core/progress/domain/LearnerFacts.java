package com.tutorcraft.core.progress.domain;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Факты о студенте для условий доступа: выполненные элементы, проценты опубликованных оценок, группы курса.
 */
public record LearnerFacts(Set<UUID> completedItems, Map<UUID, Double> gradePercents, Set<UUID> groupIds) {

    public LearnerFacts {
        completedItems = Set.copyOf(completedItems);
        gradePercents = Map.copyOf(gradePercents);
        groupIds = Set.copyOf(groupIds);
    }

    public static LearnerFacts none() {
        return new LearnerFacts(Set.of(), Map.of(), Set.of());
    }
}
