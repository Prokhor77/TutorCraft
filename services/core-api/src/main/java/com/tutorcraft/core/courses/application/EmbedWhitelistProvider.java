package com.tutorcraft.core.courses.application;

import java.util.Set;
import java.util.UUID;

/** Порт: белый список доменов для встраивания (настройка tenant, FR-CONTENT-01); реализация — через OrgApi. */
public interface EmbedWhitelistProvider {

    Set<String> embedWhitelist(UUID tenantId);
}
