package com.tutorcraft.core.gradebook.domain;

import java.math.BigDecimal;
import java.util.UUID;

/** Столбец журнала (элемент оценивания). sourceItemId == null — ручной столбец. */
public record GradeColumn(UUID id, UUID courseId, UUID sourceItemId, String name, BigDecimal maxScore, UUID categoryId,
                          int position) {

    public boolean hasPositiveMax() {
        return maxScore != null && maxScore.signum() > 0;
    }
}
