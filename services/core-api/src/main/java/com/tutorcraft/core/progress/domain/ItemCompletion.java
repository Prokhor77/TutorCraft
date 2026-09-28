package com.tutorcraft.core.progress.domain;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/** Состояние выполнения элемента студентом (таблица completion_states). */
public record ItemCompletion(UUID tenantId, UUID courseId, UUID itemId, UUID userId, Set<CompletionTrigger> reached,
                             boolean manuallyMarked, boolean complete, Instant completedAt) {

    public static final String SOURCE_AUTO = "auto";
    public static final String SOURCE_MANUAL = "manual";

    public ItemCompletion {
        reached = reached.isEmpty() ? EnumSet.noneOf(CompletionTrigger.class) : EnumSet.copyOf(reached);
    }

    public static ItemCompletion empty(UUID tenantId, UUID courseId, UUID itemId, UUID userId) {
        return new ItemCompletion(tenantId, courseId, itemId, userId, Set.of(), false, false, null);
    }

    public ItemCompletion withTrigger(CompletionTrigger trigger, boolean reachedNow) {
        Set<CompletionTrigger> updated = reached.isEmpty() ? EnumSet.noneOf(CompletionTrigger.class) : EnumSet.copyOf(reached);
        if (reachedNow) {
            updated.add(trigger);
        } else {
            updated.remove(trigger);
        }
        return new ItemCompletion(tenantId, courseId, itemId, userId, updated, manuallyMarked, complete, completedAt);
    }

    public ItemCompletion withManualMark(boolean marked) {
        return new ItemCompletion(tenantId, courseId, itemId, userId, reached, marked, complete, completedAt);
    }

    /** Пересчёт по правилу; дата выполнения фиксируется при первом переходе в «выполнено». */
    public ItemCompletion evaluate(CompletionRule rule, Instant now) {
        boolean nowComplete = rule.isComplete(reached, manuallyMarked);
        Instant at = nowComplete ? (complete ? completedAt : now) : null;
        return new ItemCompletion(tenantId, courseId, itemId, userId, reached, manuallyMarked, nowComplete, at);
    }

    public String source(CompletionRule rule) {
        return rule.mode() == CompletionRule.Mode.MANUAL ? SOURCE_MANUAL : SOURCE_AUTO;
    }
}
