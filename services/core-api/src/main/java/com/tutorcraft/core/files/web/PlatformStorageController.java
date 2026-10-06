package com.tutorcraft.core.files.web;

import com.tutorcraft.core.files.application.StorageUsageService;
import com.tutorcraft.core.files.application.StorageUsageService.TenantStorageView;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Занятое школами место — эндпоинт главного администратора платформы. */
@RestController
@RequestMapping("/api/v1/platform/storage")
class PlatformStorageController {

    private final StorageUsageService usage;

    PlatformStorageController(StorageUsageService usage) {
        this.usage = usage;
    }

    @GetMapping
    List<TenantStorageView> byTenant() {
        return usage.byTenant();
    }
}
