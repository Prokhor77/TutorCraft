package com.tutorcraft.core.org.web;

import com.tutorcraft.core.org.application.PlatformTenantsService;
import com.tutorcraft.core.org.application.TenantDeletionService;
import com.tutorcraft.core.org.application.TenantRepository.TenantSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Эндпоинты главного администратора платформы. */
@RestController
@RequestMapping("/api/v1/platform")
class PlatformController {

    private static final int MAX_SLUG = 100;

    private final PlatformTenantsService tenants;
    private final TenantDeletionService deletion;

    PlatformController(PlatformTenantsService tenants, TenantDeletionService deletion) {
        this.tenants = tenants;
        this.deletion = deletion;
    }

    @GetMapping("/tenants")
    List<TenantSummary> listTenants(@RequestParam(required = false) String q) {
        return tenants.list(q);
    }

    /** Полное удаление школы со всеми пользователями (включая владельца) и данными. */
    @DeleteMapping("/tenants/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteTenant(@PathVariable UUID id, @Valid @RequestBody DeleteTenantRequest request) {
        deletion.delete(id, request.confirmSlug());
    }

    record DeleteTenantRequest(@NotBlank @Size(max = MAX_SLUG) String confirmSlug) {
    }
}
