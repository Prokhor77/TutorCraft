package com.tutorcraft.core.enrollment.web;

import com.tutorcraft.core.enrollment.application.InviteLinkService;
import com.tutorcraft.core.enrollment.application.InviteLinkView;
import com.tutorcraft.core.enrollment.application.JoinService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Ссылки-приглашения на курс (контракт §6, FR-ENROL-03). */
@RestController
@RequestMapping("/api/v1")
class InviteLinksController {

    private static final int MAX_ROLE_LENGTH = 32;
    private static final int MAX_TOKEN_LENGTH = 128;

    private final InviteLinkService links;
    private final JoinService join;

    InviteLinksController(InviteLinkService links, JoinService join) {
        this.links = links;
        this.join = join;
    }

    @PostMapping("/courses/{courseId}/invite-links")
    @ResponseStatus(HttpStatus.CREATED)
    InviteLinkView.Created create(@PathVariable UUID courseId, @Valid @RequestBody CreateInviteLinkRequest request) {
        return links.create(courseId, request.role(), request.expiresAt(), request.maxUses());
    }

    @GetMapping("/courses/{courseId}/invite-links")
    List<InviteLinkView> list(@PathVariable UUID courseId) {
        return links.list(courseId);
    }

    @DeleteMapping("/invite-links/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@PathVariable UUID id) {
        links.revoke(id);
    }

    @PostMapping("/invite-links/accept")
    AcceptResponse accept(@Valid @RequestBody AcceptRequest request) {
        return new AcceptResponse(join.acceptInvite(request.token()));
    }

    record CreateInviteLinkRequest(@NotBlank @Size(max = MAX_ROLE_LENGTH) String role, Instant expiresAt, Integer maxUses) {
    }

    record AcceptRequest(@NotBlank @Size(max = MAX_TOKEN_LENGTH) String token) {
    }

    record AcceptResponse(UUID courseId) {
    }
}
