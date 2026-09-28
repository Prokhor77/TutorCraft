package com.tutorcraft.core.progress.domain;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/** Процент выполнения и порог «проходной оценки». */
public final class ProgressMath {

    /** Порог «получена проходная оценка», если у активности нет своего passPercent. */
    public static final double DEFAULT_PASS_PERCENT = 50.0;
    private static final int FULL = 100;

    private ProgressMath() {
    }

    /** Доля выполненных отслеживаемых элементов, 0..100 (с округлением вниз); null — отслеживаемых нет. */
    public static Integer percent(Collection<UUID> trackedItems, Set<UUID> completedItems) {
        if (trackedItems.isEmpty()) {
            return null;
        }
        long done = trackedItems.stream().filter(completedItems::contains).count();
        return (int) (done * FULL / trackedItems.size());
    }

    public static boolean passed(Double percent, Double passPercent) {
        double threshold = passPercent == null ? DEFAULT_PASS_PERCENT : passPercent;
        return percent != null && percent >= threshold;
    }
}
