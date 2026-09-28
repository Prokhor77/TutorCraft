package com.tutorcraft.core.enrollment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tutorcraft.core.enrollment.domain.SelfEnrolPolicy.Request;
import com.tutorcraft.core.shared.domain.BusinessRuleException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** FR-ENROL-02 и гибрид FR-ENROL-09: правила самозаписи. */
class SelfEnrolPolicyTest {

    private static final Instant NOW = Instant.parse("2026-09-27T10:00:00Z");

    private static String rejection(Request request) {
        try {
            SelfEnrolPolicy.check(request);
            return null;
        } catch (BusinessRuleException e) {
            return e.code();
        }
    }

    @Test
    void freeOpenCourseAllowsEnrolment() {
        assertThatCode(() -> SelfEnrolPolicy.check(new Request(true, null, null, null, false, null, 0, NOW)))
                .doesNotThrowAnyException();
    }

    @Test
    void disabledSelfEnrolIsRejected() {
        assertThat(rejection(new Request(false, null, null, null, false, null, 0, NOW))).isEqualTo(SelfEnrolPolicy.DISABLED);
    }

    @Test
    void enrolmentPeriodEnds() {
        assertThat(rejection(new Request(true, null, null, NOW, false, null, 0, NOW))).isEqualTo(SelfEnrolPolicy.CLOSED);
        assertThat(rejection(new Request(true, null, null, NOW.plusSeconds(1), false, null, 0, NOW))).isNull();
    }

    @Test
    void paidCourseWithoutCodeRequiresPayment() {
        assertThat(rejection(new Request(true, null, null, null, true, "anything", 0, NOW)))
                .isEqualTo(SelfEnrolPolicy.PAYMENT_REQUIRED);
    }

    @Test
    void paidCourseWithDisabledSelfEnrolRequiresPayment() {
        assertThat(rejection(new Request(false, "VIP", null, null, true, "VIP", 0, NOW)))
                .isEqualTo(SelfEnrolPolicy.PAYMENT_REQUIRED);
    }

    @Test
    void paidCourseAcceptsTeacherIssuedCode() {
        assertThat(rejection(new Request(true, "VIP-2026", null, null, true, " VIP-2026 ", 0, NOW))).isNull();
        assertThat(rejection(new Request(true, "VIP-2026", null, null, true, "vip-2026", 0, NOW)))
                .isEqualTo(SelfEnrolPolicy.INVALID_CODE);
    }

    @Test
    void codeIsRequiredWhenConfigured() {
        assertThat(rejection(new Request(true, "KEY", null, null, false, null, 0, NOW))).isEqualTo(SelfEnrolPolicy.INVALID_CODE);
        assertThat(rejection(new Request(true, "KEY", null, null, false, "KEY", 0, NOW))).isNull();
    }

    @Test
    void seatLimitIsEnforced() {
        assertThat(rejection(new Request(true, null, 30, null, false, null, 29, NOW))).isNull();
        assertThat(rejection(new Request(true, null, 30, null, false, null, 30, NOW))).isEqualTo(SelfEnrolPolicy.COURSE_FULL);
    }

    @Test
    void codeComparisonHandlesNull() {
        assertThat(SelfEnrolPolicy.codesMatch("A", null)).isFalse();
        assertThatThrownBy(() -> SelfEnrolPolicy.check(new Request(true, "A", null, null, false, "", 0, NOW)))
                .isInstanceOf(BusinessRuleException.class);
    }
}
