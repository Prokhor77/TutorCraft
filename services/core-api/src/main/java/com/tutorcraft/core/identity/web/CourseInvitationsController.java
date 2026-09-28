package com.tutorcraft.core.identity.web;

import com.tutorcraft.core.identity.application.CourseInvitationService;
import com.tutorcraft.core.identity.application.CourseInvitationService.CourseInvitationResult;
import com.tutorcraft.core.identity.application.CourseInvitationService.InviteCommand;
import com.tutorcraft.core.identity.application.UserRepository.UserSummaryView;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.api.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Приглашение в курс преподавателем и выбор пользователей школы для записи (контракт §6.1). */
@RestController
@RequestMapping("/api/v1/courses/{courseId}")
class CourseInvitationsController {

    private static final int MAX_EMAIL = 254;
    private static final int MAX_NAME = 100;
    private static final int MAX_ROLE = 32;

    private final CourseInvitationService invitations;

    CourseInvitationsController(CourseInvitationService invitations) {
        this.invitations = invitations;
    }

    @PostMapping("/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    CourseInvitationResult invite(@PathVariable UUID courseId, @Valid @RequestBody InviteRequest request) {
        return invitations.invite(courseId, new InviteCommand(request.email(), request.firstName(), request.lastName(),
                request.role()));
    }

    @GetMapping("/enrollment-candidates")
    PageResponse<UserSummaryView> candidates(@PathVariable UUID courseId, @RequestParam(required = false) String q,
                                             @RequestParam(required = false) String cursor,
                                             @RequestParam(required = false) Integer limit) {
        return invitations.candidates(courseId, q, PageQuery.of(cursor, limit));
    }

    record InviteRequest(@NotBlank @Size(max = MAX_EMAIL) String email, @NotBlank @Size(max = MAX_NAME) String firstName,
                         @NotBlank @Size(max = MAX_NAME) String lastName, @NotBlank @Size(max = MAX_ROLE) String role) {
    }
}
