package com.tutorcraft.core.courses.spi;

import com.tutorcraft.core.courses.ItemRef;
import com.tutorcraft.core.courses.ItemType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Статус активности для студента: {@code not_started | in_progress | draft | submitted | submitted_late | graded | returned}.
 * Реализуют assignment (C) и quiz (D). Используется в оглавлении курса и «Моих задачах».
 */
public interface ItemStatusProvider {

    Set<ItemType> supportedTypes();

    Map<UUID, String> statuses(UUID tenantId, UUID userId, List<ItemRef> items);
}
