package com.tutorcraft.core.enrollment.domain;

import com.tutorcraft.core.access.domain.CourseRole;
import com.tutorcraft.core.shared.domain.Validator;
import java.time.Instant;
import java.util.UUID;

/** Запись пользователя на курс (FR-ENROL-01/04): роль, статус, способ, окно доступа [startsAt, endsAt). */
public record Enrollment(UUID id, UUID tenantId, UUID courseId, UUID userId, CourseRole role, EnrollmentStatus status,
                         String method, Instant startsAt, Instant endsAt, Instant createdAt, Instant updatedAt,
                         Instant lastAccessAt) {

    /** Даёт доступ: статус active и now ∈ [startsAt, endsAt). */
    public boolean activeAt(Instant now) {
        return status == EnrollmentStatus.ACTIVE
                && (startsAt == null || !now.isBefore(startsAt))
                && (endsAt == null || now.isBefore(endsAt));
    }

    public Enrollment withChanges(CourseRole newRole, EnrollmentStatus newStatus, Instant newStartsAt, Instant newEndsAt) {
        return new Enrollment(id, tenantId, courseId, userId, newRole, newStatus, method, newStartsAt, newEndsAt, createdAt,
                updatedAt, lastAccessAt);
    }

    public static void validateWindow(Instant startsAt, Instant endsAt) {
        new Validator()
                .check(startsAt == null || endsAt == null || endsAt.isAfter(startsAt), "endsAt", "before_start",
                        "End date must be after start date")
                .throwIfInvalid();
    }
}
