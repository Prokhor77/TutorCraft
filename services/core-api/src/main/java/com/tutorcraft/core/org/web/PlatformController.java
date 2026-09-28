package com.tutorcraft.core.org.web;

import com.tutorcraft.core.org.application.PlatformTenantsService;
import com.tutorcraft.core.org.application.TenantRepository.TenantSummary;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Эндпоинты главного администратора платформы. */
@RestController
@RequestMapping("/api/v1/platform")
class PlatformController {

    private final PlatformTenantsService tenants;

    PlatformController(PlatformTenantsService tenants) {
        this.tenants = tenants;
    }

    @GetMapping("/tenants")
    List<TenantSummary> listTenants(@RequestParam(required = false) String q) {
        return tenants.list(q);
    }
}
