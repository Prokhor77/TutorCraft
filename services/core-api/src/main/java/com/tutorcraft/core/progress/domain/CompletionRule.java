package com.tutorcraft.core.progress.domain;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * Правило выполнения элемента (контракт §5 ItemCompletionRule): none — не отслеживается; manual — отметка студентом;
 * auto — выполнено, когда наступили все события из {@code on}.
 */
public record CompletionRule(Mode mode, Set<CompletionTrigger> on) {

    public enum Mode { NONE, MANUAL, AUTO }

    public static CompletionRule of(String modeKey, Collection<String> triggers) {
        Set<CompletionTrigger> on = EnumSet.noneOf(CompletionTrigger.class);
        triggers.forEach(key -> CompletionTrigger.find(key).ifPresent(on::add));
        return new CompletionRule(mode(modeKey), on);
    }

    private static Mode mode(String key) {
        if ("manual".equals(key)) {
            return Mode.MANUAL;
        }
        return "auto".equals(key) ? Mode.AUTO : Mode.NONE;
    }

    public boolean tracked() {
        return mode != Mode.NONE;
    }

    public boolean isComplete(Set<CompletionTrigger> reached, boolean manuallyMarked) {
        return switch (mode) {
            case NONE -> false;
            case MANUAL -> manuallyMarked;
            case AUTO -> !on.isEmpty() && reached.containsAll(on);
        };
    }
}
