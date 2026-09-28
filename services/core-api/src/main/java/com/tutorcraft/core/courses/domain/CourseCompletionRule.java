package com.tutorcraft.core.courses.domain;

import com.tutorcraft.core.shared.domain.Validator;
import java.util.List;
import java.util.UUID;

/** Правило завершения курса (FR-PROG-05): обязательные элементы и/или минимальный итоговый процент. */
public record CourseCompletionRule(List<UUID> requiredItemIds, Double minFinalPercent) {

    public static final CourseCompletionRule EMPTY = new CourseCompletionRule(List.of(), null);
    public static final int MAX_REQUIRED_ITEMS = 1000;
    private static final double MAX_PERCENT = 100.0;

    public CourseCompletionRule {
        requiredItemIds = requiredItemIds == null ? List.of() : List.copyOf(requiredItemIds);
    }

    void validate(Validator validator) {
        validator.check(requiredItemIds.size() <= MAX_REQUIRED_ITEMS, "completionRule.requiredItemIds", "too_many",
                        "Too many required items")
                .check(minFinalPercent == null || (minFinalPercent >= 0 && minFinalPercent <= MAX_PERCENT),
                        "completionRule.minFinalPercent", "out_of_range", "minFinalPercent must be between 0 and 100");
    }
}
