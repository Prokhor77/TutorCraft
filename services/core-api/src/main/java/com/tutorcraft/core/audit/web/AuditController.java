package com.tutorcraft.core.audit.web;

import com.tutorcraft.core.audit.application.AuditQueryService;
import com.tutorcraft.core.audit.application.AuditQueryService.AuditEntryView;
import com.tutorcraft.core.audit.application.AuditQueryService.AuditFilter;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.api.PageResponse;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-log")
class AuditController {

    private final AuditQueryService service;

    AuditController(AuditQueryService service) {
        this.service = service;
    }

    @GetMapping
    PageResponse<AuditEntryView> search(@RequestParam(required = false) UUID actorId,
                                        @RequestParam(required = false) String objectType,
                                        @RequestParam(required = false) Instant from,
                                        @RequestParam(required = false) Instant to,
                                        @RequestParam(defaultValue = "false") boolean allTenants,
                                        @RequestParam(required = false) String cursor,
                                        @RequestParam(required = false) Integer limit) {
        return service.search(new AuditFilter(actorId, objectType, from, to, allTenants), PageQuery.of(cursor, limit));
    }
}
