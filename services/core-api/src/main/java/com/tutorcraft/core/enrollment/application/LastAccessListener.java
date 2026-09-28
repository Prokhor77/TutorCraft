package com.tutorcraft.core.enrollment.application;

import com.tutorcraft.core.courses.CourseEvents.ItemViewed;
import java.time.Clock;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Время последнего обращения участника к курсу (Enrollment.lastAccessAt) — по открытию элемента. */
@Component
class LastAccessListener {

    private final EnrollmentRepository enrollments;
    private final Clock clock;

    LastAccessListener(EnrollmentRepository enrollments, Clock clock) {
        this.enrollments = enrollments;
        this.clock = clock;
    }

    @EventListener
    public void onItemViewed(ItemViewed event) {
        enrollments.touchLastAccess(event.tenantId(), event.courseId(), event.userId(), clock.instant());
    }
}
