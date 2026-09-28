package com.tutorcraft.core.communication.notifications.application;

import com.tutorcraft.core.access.domain.CourseRole;
import com.tutorcraft.core.courses.ItemRef;
import com.tutorcraft.core.courses.spi.ItemStatusProvider;
import com.tutorcraft.core.enrollment.EnrollmentApi;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Получатели уведомлений курса: активные студенты; для дедлайнов — те, кто ещё не сдал. */
@Component
class CourseAudience {

    private static final Set<CourseRole> STUDENTS = Set.of(CourseRole.STUDENT);
    private static final Set<String> DONE_STATUSES = Set.of("submitted", "submitted_late", "graded");

    private final EnrollmentApi enrollment;
    private final List<ItemStatusProvider> statusProviders;

    CourseAudience(EnrollmentApi enrollment, List<ItemStatusProvider> statusProviders) {
        this.enrollment = enrollment;
        this.statusProviders = List.copyOf(statusProviders);
    }

    List<UUID> activeStudents(UUID tenantId, UUID courseId) {
        return enrollment.activeMembers(tenantId, courseId, STUDENTS).stream().map(EnrollmentApi.Member::userId).toList();
    }

    /** Студенты, у которых активность ещё не сдана (для элементов без провайдера статуса — все). */
    List<UUID> studentsWithPendingWork(ItemRef item) {
        Optional<ItemStatusProvider> provider = statusProviders.stream()
                .filter(candidate -> candidate.supportedTypes().contains(item.type()))
                .findFirst();
        List<UUID> students = activeStudents(item.tenantId(), item.courseId());
        if (provider.isEmpty()) {
            return students;
        }
        return students.stream()
                .filter(userId -> !DONE_STATUSES.contains(provider.get().statuses(item.tenantId(), userId, List.of(item))
                        .get(item.id())))
                .toList();
    }
}
