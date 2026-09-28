package com.tutorcraft.core.courses.infrastructure;

import com.tutorcraft.core.courses.application.EmbedWhitelistProvider;
import com.tutorcraft.core.org.OrgApi;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Белый список встраиваний из настроек tenant — через публичный API модуля org (владелец таблицы tenants). */
@Component
class OrgEmbedWhitelistProvider implements EmbedWhitelistProvider {

    private final OrgApi org;

    OrgEmbedWhitelistProvider(OrgApi org) {
        this.org = org;
    }

    @Override
    public Set<String> embedWhitelist(UUID tenantId) {
        return org.embedWhitelist(tenantId);
    }
}
