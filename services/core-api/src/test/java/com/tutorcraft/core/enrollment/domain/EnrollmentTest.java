package com.tutorcraft.core.enrollment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tutorcraft.core.access.domain.CourseRole;
import com.tutorcraft.core.shared.domain.ValidationException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** FR-ENROL-04: доступ даёт только активная запись в окне дат [startsAt, endsAt). */
class EnrollmentTest {

    private static final Instant NOW = Instant.parse("2026-09-27T10:00:00Z");

    private static Enrollment enrollment(EnrollmentStatus status, Instant startsAt, Instant endsAt) {
        return new Enrollment(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), CourseRole.STUDENT,
                status, "manual", startsAt, endsAt, NOW, NOW, null);
    }

    @Test
    void activeWithoutDatesGivesAccess() {
        assertThat(enrollment(EnrollmentStatus.ACTIVE, null, null).activeAt(NOW)).isTrue();
    }

    @Test
    void suspendedAndCompletedDoNotGiveAccess() {
        assertThat(enrollment(EnrollmentStatus.SUSPENDED, null, null).activeAt(NOW)).isFalse();
        assertThat(enrollment(EnrollmentStatus.COMPLETED, null, null).activeAt(NOW)).isFalse();
    }

    @Test
    void windowIsStartInclusiveEndExclusive() {
        Enrollment windowed = enrollment(EnrollmentStatus.ACTIVE, NOW, NOW.plusSeconds(60));

        assertThat(windowed.activeAt(NOW.minusSeconds(1))).isFalse();
        assertThat(windowed.activeAt(NOW)).isTrue();
        assertThat(windowed.activeAt(NOW.plusSeconds(60))).isFalse();
    }

    @Test
    void windowMustBeOrdered() {
        assertThatThrownBy(() -> Enrollment.validateWindow(NOW, NOW)).isInstanceOf(ValidationException.class);
        Enrollment.validateWindow(null, NOW);
        Enrollment.validateWindow(NOW, null);
    }

    @Test
    void statusKeys() {
        assertThat(EnrollmentStatus.fromKey("suspended")).isEqualTo(EnrollmentStatus.SUSPENDED);
        assertThatThrownBy(() -> EnrollmentStatus.fromKey("paused")).isInstanceOf(ValidationException.class);
    }
}
