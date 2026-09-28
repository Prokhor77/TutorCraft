package com.tutorcraft.core.identity.web;

import com.tutorcraft.core.identity.application.IdentityErrors;
import com.tutorcraft.core.identity.application.UserAdminService;
import com.tutorcraft.core.identity.application.UserAdminService.CreateUserCommand;
import com.tutorcraft.core.identity.application.UserAdminService.UpdateUserCommand;
import com.tutorcraft.core.identity.application.UserImportService;
import com.tutorcraft.core.identity.application.UserImportService.ImportCommitResult;
import com.tutorcraft.core.identity.application.UserImportService.ImportPreviewView;
import com.tutorcraft.core.identity.application.UserRepository.UserFilter;
import com.tutorcraft.core.identity.application.UserRepository.UserSummaryView;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.api.PageResponse;
import com.tutorcraft.core.shared.domain.BusinessRuleException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Пользователи tenant (контракт §4). */
@RestController
@RequestMapping("/api/v1/users")
class UsersController {

    private static final int MAX_TEXT = 254;
    private static final int MAX_NAME = 100;

    private final UserAdminService admin;
    private final UserImportService imports;

    UsersController(UserAdminService admin, UserImportService imports) {
        this.admin = admin;
        this.imports = imports;
    }

    @GetMapping
    PageResponse<UserSummaryView> list(@RequestParam(required = false) String q,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(required = false) String role,
                                       @RequestParam(required = false) String cursor,
                                       @RequestParam(required = false) Integer limit) {
        return admin.list(new UserFilter(q, status, role), PageQuery.of(cursor, limit));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    UserSummaryView create(@Valid @RequestBody CreateUserRequest request) {
        return admin.create(new CreateUserCommand(request.email(), request.firstName(), request.lastName(),
                request.tenantRoles(), request.sendInvite()));
    }

    @GetMapping("/{id}")
    UserSummaryView get(@PathVariable UUID id) {
        return admin.get(id);
    }

    @PatchMapping("/{id}")
    UserSummaryView update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return admin.update(id, new UpdateUserCommand(request.firstName(), request.lastName(), request.status(),
                request.tenantRoles()));
    }

    @PostMapping("/{id}/invite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resendInvite(@PathVariable UUID id) {
        admin.resendInvitation(id);
    }

    @PostMapping(path = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ImportPreviewView importPreview(@RequestPart("file") MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            return imports.preview(input, file.getSize());
        } catch (IOException e) {
            throw new BusinessRuleException(IdentityErrors.IMPORT_UNREADABLE, "Uploaded file cannot be read");
        }
    }

    @PostMapping("/import/commit")
    ImportCommitResult importCommit(@Valid @RequestBody ImportCommitRequest request) {
        return imports.commit(request.previewId());
    }

    record CreateUserRequest(@NotBlank @Size(max = MAX_TEXT) String email, @NotBlank @Size(max = MAX_NAME) String firstName,
                             @NotBlank @Size(max = MAX_NAME) String lastName, List<String> tenantRoles,
                             boolean sendInvite) {
    }

    record UpdateUserRequest(@Size(max = MAX_NAME) String firstName, @Size(max = MAX_NAME) String lastName, String status,
                             List<String> tenantRoles) {
    }

    record ImportCommitRequest(@NotNull UUID previewId) {
    }
}
