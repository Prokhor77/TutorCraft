package com.tutorcraft.core.audit.application;

import com.tutorcraft.core.shared.api.PageQuery;
import java.util.List;
import java.util.UUID;

public interface AuditQueryRepository {

    List<AuditQueryService.AuditEntryView> search(UUID tenantId, AuditQueryService.AuditFilter filter, PageQuery page);
}
