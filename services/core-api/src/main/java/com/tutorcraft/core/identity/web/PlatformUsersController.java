package com.tutorcraft.core.identity.web;

import com.tutorcraft.core.identity.application.PlatformUsersService;
import com.tutorcraft.core.identity.application.SchoolMembersService.MemberQuery;
import com.tutorcraft.core.identity.web.MemberResponses.PlatformUser;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.api.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Пользователи всех школ для главного администратора (контракт §4.2). */
@RestController
@RequestMapping("/api/v1/platform/users")
class PlatformUsersController {

    private static final int MAX_REASON = 500;

    private final PlatformUsersService users;

    PlatformUsersController(PlatformUsersService users) {
        this.users = users;
    }

    @GetMapping
    PageResponse<PlatformUser> list(@RequestParam(required = false) UUID tenantId,
                                    @RequestParam(required = false) String q,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(required = false) String origin,
                                    @RequestParam(required = false) String cursor,
                                    @RequestParam(required = false) Integer limit) {
        return users.list(tenantId, new MemberQuery(q, status, origin), PageQuery.of(cursor, limit))
                .map(MemberResponses::platform);
    }

    @PostMapping("/{id}/block")
    PlatformUser block(@PathVariable UUID id, @Valid @RequestBody(required = false) BlockRequest request) {
        return MemberResponses.platform(users.block(id, request == null ? null : request.reason()));
    }

    @PostMapping("/{id}/unblock")
    PlatformUser unblock(@PathVariable UUID id) {
        return MemberResponses.platform(users.unblock(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void erase(@PathVariable UUID id) {
        users.erase(id);
    }

    record BlockRequest(@Size(max = MAX_REASON) String reason) {
    }
}
