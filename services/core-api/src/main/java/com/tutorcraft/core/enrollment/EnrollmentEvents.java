package com.tutorcraft.core.enrollment;

import java.util.UUID;

public final class EnrollmentEvents {

    private EnrollmentEvents() {
    }

    public record EnrollmentChanged(UUID tenantId, UUID courseId, UUID userId, String roleKey, String status,
                                    String method, boolean created) {
    }
}
