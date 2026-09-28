package com.tutorcraft.core.enrollment.application;

import com.tutorcraft.core.access.spi.CourseMembershipResolver;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Порт access: роль по активной записи (status=active и now ∈ [starts_at, ends_at)). Только репозиторий. */
@Component
class CourseMembershipAdapter implements CourseMembershipResolver {

    private final EnrollmentRepository enrollments;
    private final Clock clock;

    CourseMembershipAdapter(EnrollmentRepository enrollments, Clock clock) {
        this.enrollments = enrollments;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> activeRoleKey(UUID tenantId, UUID userId, UUID courseId) {
        return enrollments.activeRoleKey(tenantId, userId, courseId, clock.instant());
    }
}
