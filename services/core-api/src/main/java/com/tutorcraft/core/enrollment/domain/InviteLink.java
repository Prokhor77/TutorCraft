package com.tutorcraft.core.enrollment.domain;

import com.tutorcraft.core.access.domain.CourseRole;
import com.tutorcraft.core.shared.domain.Validator;
import java.time.Instant;
import java.util.UUID;

/** Ссылка-приглашение на курс (FR-ENROL-03): TTL и лимит использований; токен хранится только хешем. */
public record InviteLink(UUID id, UUID tenantId, UUID courseId, String tokenHash, CourseRole role, Instant expiresAt,
                         Integer maxUses, int uses, Instant revokedAt, UUID createdBy, Instant createdAt) {

    public static final int MAX_USES_LIMIT = 10_000;

    public enum Validity { VALID, EXPIRED, REVOKED, EXHAUSTED }

    public Validity validityAt(Instant now) {
        if (revokedAt != null) {
            return Validity.REVOKED;
        }
        if (expiresAt != null && !now.isBefore(expiresAt)) {
            return Validity.EXPIRED;
        }
        if (maxUses != null && uses >= maxUses) {
            return Validity.EXHAUSTED;
        }
        return Validity.VALID;
    }

    public boolean usableAt(Instant now) {
        return validityAt(now) == Validity.VALID;
    }

    /** Параметры новой ссылки: срок — в будущем, лимит — 1..MAX_USES_LIMIT (оба необязательны). */
    public static void validateNew(Instant expiresAt, Integer maxUses, Instant now) {
        new Validator()
                .check(expiresAt == null || expiresAt.isAfter(now), "expiresAt", "in_past", "Expiry must be in the future")
                .check(maxUses == null || (maxUses >= 1 && maxUses <= MAX_USES_LIMIT), "maxUses", "out_of_range",
                        "maxUses must be between 1 and " + MAX_USES_LIMIT)
                .throwIfInvalid();
    }
}
