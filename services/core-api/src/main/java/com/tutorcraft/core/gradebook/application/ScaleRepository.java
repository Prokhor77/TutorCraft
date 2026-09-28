package com.tutorcraft.core.gradebook.application;

import com.tutorcraft.core.gradebook.domain.ScaleLevel;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Шкалы оценивания уровня tenant и курса (FR-GRADE-05). */
public interface ScaleRepository {

    /** Шкалы tenant и, если courseId задан, шкалы этого курса. */
    List<Scale> list(UUID tenantId, UUID courseId);

    Optional<Scale> find(UUID tenantId, UUID scaleId);

    boolean hasTenantScales(UUID tenantId);

    /** Вставка; шкала tenant с тем же именем не дублируется. */
    void insert(Scale scale, Instant now);

    record Scale(UUID id, UUID tenantId, UUID courseId, String name, List<ScaleLevel> levels) {
    }
}
