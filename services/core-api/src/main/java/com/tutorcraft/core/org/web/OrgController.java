package com.tutorcraft.core.org.web;

import com.tutorcraft.core.org.OrgApi.PasswordPolicy;
import com.tutorcraft.core.org.application.CategoryService;
import com.tutorcraft.core.org.application.CategoryService.CategoryView;
import com.tutorcraft.core.org.application.TenantRepository.TenantSettingsUpdate;
import com.tutorcraft.core.org.application.TenantRepository.TenantSettingsView;
import com.tutorcraft.core.org.application.TenantSettingsService;
import com.tutorcraft.core.shared.api.IfMatch;
import com.tutorcraft.core.files.FilesApi;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class OrgController {

    private final TenantSettingsService settings;
    private final CategoryService categories;
    private final FilesApi files;

    OrgController(TenantSettingsService settings, CategoryService categories, FilesApi files) {
        this.settings = settings;
        this.categories = categories;
        this.files = files;
    }

    @GetMapping("/tenant")
    TenantSettingsResponse getTenant() {
        return toResponse(settings.get());
    }

    @PatchMapping("/tenant")
    TenantSettingsResponse updateTenant(@RequestHeader(value = IfMatch.HEADER, required = false) String ifMatch,
                                        @Valid @RequestBody TenantSettingsRequest request) {
        long version = IfMatch.resolve(ifMatch, request.version());
        TenantSettingsUpdate update = new TenantSettingsUpdate(request.name(), request.logoFileId(), request.primaryColor(),
                request.defaultLocale(), request.defaultTimezone(), request.passwordPolicy(), request.embedWhitelist());
        return toResponse(settings.update(update, version));
    }

    @GetMapping("/categories")
    List<CategoryView> listCategories() {
        return categories.list();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    CategoryView createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        return categories.create(request.name(), request.parentId());
    }

    @PatchMapping("/categories/{id}")
    CategoryView updateCategory(@PathVariable UUID id, @RequestBody UpdateCategoryRequest request) {
        return categories.update(id, request.name(), request.parentId(), request.moveToParent(), request.position());
    }

    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteCategory(@PathVariable UUID id) {
        categories.delete(id);
    }

    private TenantSettingsResponse toResponse(TenantSettingsView view) {
        String logoUrl = view.logoFileId() == null ? null
                : files.find(view.id(), view.logoFileId()).map(files::downloadUrl).orElse(null);
        return new TenantSettingsResponse(view.id(), view.slug(), view.name(), new Branding(logoUrl, view.primaryColor()),
                view.logoFileId(), view.defaultLocale(), view.defaultTimezone(), view.passwordPolicy(), view.embedWhitelist(),
                view.version());
    }

    record Branding(String logoUrl, String primaryColor) {
    }

    record TenantSettingsResponse(UUID id, String slug, String name, Branding branding, UUID logoFileId, String defaultLocale,
                                  String defaultTimezone, PasswordPolicy passwordPolicy, List<String> embedWhitelist,
                                  long version) {
    }

    record TenantSettingsRequest(@NotBlank String name, UUID logoFileId, String primaryColor, @NotBlank String defaultLocale,
                                 @NotBlank String defaultTimezone, PasswordPolicy passwordPolicy, List<String> embedWhitelist,
                                 Long version) {
    }

    record CreateCategoryRequest(@NotBlank String name, UUID parentId) {
    }

    /** moveToParent=true — применить parentId (включая null = в корень). */
    record UpdateCategoryRequest(String name, UUID parentId, boolean moveToParent, Integer position) {
    }
}
