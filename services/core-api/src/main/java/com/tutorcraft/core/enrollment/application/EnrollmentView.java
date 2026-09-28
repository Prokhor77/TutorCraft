package com.tutorcraft.core.enrollment.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Enrollment (контракт §6). */
public record EnrollmentView(UUID id, UserView user, String role, String status, String method, Instant startsAt,
                             Instant endsAt, List<UUID> groupIds, Instant lastAccessAt) {

    public record UserView(UUID id, String firstName, String lastName, String email, String avatarUrl) {
    }
}
