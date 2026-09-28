package com.tutorcraft.core.identity.application;

import com.tutorcraft.core.identity.application.SchoolMembersService.MemberQuery;
import com.tutorcraft.core.identity.domain.AccountOrigin;
import com.tutorcraft.core.identity.domain.UserStatus;
import com.tutorcraft.core.shared.domain.ValidationException;
import com.tutorcraft.core.shared.domain.Validator;
import java.util.Set;

/** Проверка фильтров списков участников (школа и платформа). */
final class MemberQueries {

    static final String STATUS_PLATFORM_BLOCKED = "blocked";
    private static final int MAX_QUERY_LENGTH = 100;

    private MemberQueries() {
    }

    static void validate(MemberQuery query) {
        new Validator()
            .maxLength(query.q(), MAX_QUERY_LENGTH, "q")
            .check(query.status() == null || STATUS_PLATFORM_BLOCKED.equals(query.status())
                    || UserStatus.find(query.status()).isPresent(), "status", "invalid", "Unknown status")
            .check(query.origin() == null || AccountOrigin.find(query.origin()).isPresent(), "origin", "invalid",
                    "Unknown origin")
            .throwIfInvalid();
    }

    static String trim(String query) {
        return query == null || query.isBlank() ? null : query.strip();
    }

    static UserStatus parseSettableStatus(String key, Set<UserStatus> allowed) {
        return UserStatus.find(key).filter(allowed::contains)
                .orElseThrow(() -> ValidationException.single("status", "invalid", "Allowed: active, suspended"));
    }
}
