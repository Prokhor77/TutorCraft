package com.tutorcraft.core.courses;

import com.tutorcraft.core.shared.domain.Money;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Снимок курса для других модулей (источник истины — PostgreSQL, таблица courses). */
public record CourseRef(UUID id, UUID tenantId, String title, String shortName, String slug, UUID categoryId, Visibility visibility,
                        Instant publishAt, Instant startsAt, Instant endsAt, Money price,
                        List<UUID> requiredItemIds, Double minFinalPercent, String groupMode, UUID createdBy) {
}
