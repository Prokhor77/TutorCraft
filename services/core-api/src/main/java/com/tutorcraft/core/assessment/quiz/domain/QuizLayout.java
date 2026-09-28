package com.tutorcraft.core.assessment.quiz.domain;

import com.tutorcraft.core.shared.domain.Validator;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Состав теста (контракт §10, PUT /items/{id}/quiz/slots). */
public record QuizLayout(List<LayoutSlot> slots) {

    static final int MAX_SLOTS = 200;
    static final int MAX_RANDOM_COUNT = 100;
    static final int MAX_TAG = 64;
    static final BigDecimal MAX_POINTS = BigDecimal.valueOf(1000);

    public static QuizLayout empty() {
        return new QuizLayout(List.of());
    }

    /** @throws com.tutorcraft.core.shared.domain.ValidationException поля {@code slots[i].*} */
    public static QuizLayout of(List<LayoutSlot> slots) {
        Validator validator = new Validator();
        validator.check(slots.size() <= MAX_SLOTS, "slots", "too_many", "At most " + MAX_SLOTS + " slots are allowed");
        Set<UUID> fixedIds = new HashSet<>();
        for (int i = 0; i < slots.size(); i++) {
            validateSlot(slots.get(i), "slots[" + i + "]", fixedIds, validator);
        }
        validator.throwIfInvalid();
        return new QuizLayout(List.copyOf(slots));
    }

    private static void validateSlot(LayoutSlot slot, String field, Set<UUID> fixedIds, Validator validator) {
        validator.check(slot.points() == null || (slot.points().signum() >= 0 && slot.points().compareTo(MAX_POINTS) <= 0),
                    field + ".points", "out_of_range", "Points must be between 0 and " + MAX_POINTS)
            .check(slot.page() == null || slot.page() >= 1, field + ".page", "out_of_range", "Page must be positive");
        switch (slot) {
            case LayoutSlot.Fixed fixed -> validator.check(fixed.questionId() != null, field + ".questionId", "required",
                    "Question is required").check(fixed.questionId() == null || fixedIds.add(fixed.questionId()),
                    field + ".questionId", "duplicate", "Question is already in the quiz");
            case LayoutSlot.Random random -> validator
                    .check(random.count() >= 1 && random.count() <= MAX_RANDOM_COUNT, field + ".random.count", "out_of_range",
                            "Count must be between 1 and " + MAX_RANDOM_COUNT)
                    .check(random.categoryId() != null || (random.tag() != null && !random.tag().isBlank()),
                            field + ".random", "source_required", "Category or tag is required")
                    .maxLength(random.tag(), MAX_TAG, field + ".random.tag");
        }
    }

    public Set<UUID> fixedQuestionIds() {
        Set<UUID> ids = new HashSet<>();
        slots.forEach(slot -> {
            if (slot instanceof LayoutSlot.Fixed fixed) {
                ids.add(fixed.questionId());
            }
        });
        return ids;
    }
}
