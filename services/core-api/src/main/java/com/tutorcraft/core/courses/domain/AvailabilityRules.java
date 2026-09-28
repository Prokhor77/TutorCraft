package com.tutorcraft.core.courses.domain;

import com.tutorcraft.core.courses.Availability;
import java.util.ArrayList;
import java.util.List;

/**
 * Доступность вложенных элементов (FR-PROG-03, UX-07): элемент доступен, только если доступны он и все его
 * модули-предки; причины объединяются, режим «скрыть» побеждает «показать с замком».
 */
public final class AvailabilityRules {

    private AvailabilityRules() {
    }

    public static Availability combine(Availability outer, Availability inner) {
        Availability safeOuter = outer == null ? Availability.open() : outer;
        Availability safeInner = inner == null ? Availability.open() : inner;
        if (safeOuter.available()) {
            return safeInner;
        }
        if (safeInner.available()) {
            return safeOuter;
        }
        List<String> reasons = new ArrayList<>(safeOuter.reasons());
        safeInner.reasons().stream().filter(reason -> !reasons.contains(reason)).forEach(reasons::add);
        boolean hide = isHideMode(safeOuter) || isHideMode(safeInner);
        return new Availability(false, hide ? Availability.HIDE : Availability.SHOW_LOCKED, List.copyOf(reasons));
    }

    /** Недоступно и в режиме «скрыть» — учащемуся элемент не показывается. */
    public static boolean hiddenFromLearner(Availability availability) {
        return availability != null && !availability.available() && isHideMode(availability);
    }

    private static boolean isHideMode(Availability availability) {
        return Availability.HIDE.equals(availability.mode());
    }
}
