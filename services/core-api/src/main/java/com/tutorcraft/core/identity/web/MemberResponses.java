package com.tutorcraft.core.identity.web;

import com.tutorcraft.core.identity.application.PlatformUsersService.PlatformUserView;
import com.tutorcraft.core.identity.application.UserRepository.CreatorView;
import com.tutorcraft.core.identity.application.UserRepository.MemberView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** DTO участников школы (репетитор) и пользователей платформы (главный администратор). */
final class MemberResponses {

    private MemberResponses() {
    }

    static SchoolMember school(MemberView member) {
        return new SchoolMember(member.id(), member.email(), member.firstName(), member.lastName(), member.status(),
                member.platformBlockedAt() != null, member.origin(), creator(member.createdBy()), member.tenantRoles(),
                member.lastLoginAt(), member.createdAt());
    }

    static PlatformUser platform(PlatformUserView view) {
        MemberView member = view.member();
        return new PlatformUser(member.id(), member.email(), member.firstName(), member.lastName(), member.status(),
                member.platformBlockedAt() == null ? null
                        : new PlatformBlock(member.platformBlockedAt(), member.platformBlockReason()),
                member.origin(), creator(member.createdBy()),
                new School(view.tenant().id(), view.tenant().slug(), view.tenant().name()), member.tenantRoles(),
                member.lastLoginAt(), member.createdAt());
    }

    private static Creator creator(CreatorView creator) {
        return creator == null ? null
                : new Creator(creator.id(), creator.email(), creator.firstName(), creator.lastName());
    }

    record Creator(UUID id, String email, String firstName, String lastName) {
    }

    record School(UUID id, String slug, String name) {
    }

    record PlatformBlock(Instant at, String reason) {
    }

    record SchoolMember(UUID id, String email, String firstName, String lastName, String status, boolean platformBlocked,
                        String origin, Creator createdBy, List<String> tenantRoles, Instant lastLoginAt,
                        Instant createdAt) {
    }

    record PlatformUser(UUID id, String email, String firstName, String lastName, String status,
                        PlatformBlock platformBlock, String origin, Creator createdBy, School school,
                        List<String> tenantRoles, Instant lastLoginAt, Instant createdAt) {
    }
}
