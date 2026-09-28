package com.tutorcraft.core.courses.application;

import java.util.Set;
import java.util.UUID;

/** Белый список доменов для встраивания (tenants.embed_whitelist, FR-CONTENT-01). */
public interface EmbedWhitelistProvider {

    Set<String> embedWhitelist(UUID tenantId);
}
