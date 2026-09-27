package com.tutorcraft.core.audit.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.api.PageResponse;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditQueryService {

    private final AuditQueryRepository repository;
    private final AccessService access;
    private final CurrentUserProvider currentUser;

    public AuditQueryService(AuditQueryRepository repository, AccessService access, CurrentUserProvider currentUser) {
        this.repository = repository;
        this.access = access;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEntryView> search(AuditFilter filter, PageQuery page) {
        access.require(Permission.AUDIT_VIEW, AccessContext.tenant());
        UUID tenantId = currentUser.require().tenantId();
        return page.toPage(repository.search(tenantId, filter, page), AuditEntryView::at, AuditEntryView::id);
    }

    public record AuditFilter(UUID actorId, String objectType, Instant from, Instant to) {
    }

    public record AuditEntryView(UUID id, Instant at, UUID actorId, String actorName, String action, String objectType,
                                 String objectId, String ip, Map<String, Object> diff) {
    }
}
