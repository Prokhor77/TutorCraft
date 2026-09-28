package com.tutorcraft.core.org.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.org.application.TenantRepository.TenantSummary;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Список школ для главного администратора (выбор школы в админке). Право platform.manage. */
@Service
public class PlatformTenantsService {

    private static final int MAX_QUERY_LENGTH = 100;
    private static final int LIMIT = 200;

    private final TenantRepository tenants;
    private final AccessService access;

    PlatformTenantsService(TenantRepository tenants, AccessService access) {
        this.tenants = tenants;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public List<TenantSummary> list(String query) {
        access.require(Permission.PLATFORM_MANAGE, AccessContext.tenant());
        String trimmed = query == null ? null : query.strip();
        if (trimmed != null && trimmed.length() > MAX_QUERY_LENGTH) {
            trimmed = trimmed.substring(0, MAX_QUERY_LENGTH);
        }
        return tenants.list(trimmed, LIMIT);
    }
}
