package com.tutorcraft.core.gradebook;

import java.math.BigDecimal;
import java.util.UUID;

public final class GradebookEvents {

    private GradebookEvents() {
    }

    /** Оценка изменилась (для выполнения по «получена оценка/проходная оценка» и уведомлений). */
    public record GradeChanged(UUID tenantId, UUID courseId, UUID sourceItemId, UUID userId, BigDecimal score,
                               BigDecimal maxScore, boolean published) {
    }
}
