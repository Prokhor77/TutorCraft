package com.tutorcraft.core.identity.web;

import com.tutorcraft.core.identity.application.SchoolMembersService;
import com.tutorcraft.core.identity.application.SchoolMembersService.MemberQuery;
import com.tutorcraft.core.identity.web.MemberResponses.SchoolMember;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.api.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Ученики своей школы для репетитора-владельца (контракт §4.1). */
@RestController
@RequestMapping("/api/v1/school/members")
class SchoolMembersController {

    private static final int MAX_STATUS = 32;

    private final SchoolMembersService members;

    SchoolMembersController(SchoolMembersService members) {
        this.members = members;
    }

    @GetMapping
    PageResponse<SchoolMember> list(@RequestParam(required = false) String q,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(required = false) String origin,
                                    @RequestParam(required = false) String cursor,
                                    @RequestParam(required = false) Integer limit) {
        return members.list(new MemberQuery(q, status, origin), PageQuery.of(cursor, limit)).map(MemberResponses::school);
    }

    @PatchMapping("/{id}")
    SchoolMember changeStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
        return MemberResponses.school(members.changeStatus(id, request.status()));
    }

    @PostMapping("/{id}/activation-link")
    ActivationLink activationLink(@PathVariable UUID id) {
        return new ActivationLink(members.reissueActivationLink(id));
    }

    record StatusRequest(@NotBlank @Size(max = MAX_STATUS) String status) {
    }

    record ActivationLink(String activationUrl) {
    }
}
