package com.tutorcraft.core.access.spi;

import java.util.List;
import java.util.UUID;

/** Порт: категория и её предки (от самой категории к корню). Реализует модуль org. */
public interface CategoryAncestry {

    List<UUID> selfAndAncestors(UUID tenantId, UUID categoryId);
}
