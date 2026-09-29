package com.tutorcraft.core.enrollment.domain;

import com.tutorcraft.core.shared.domain.BusinessRuleException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

/**
 * Правила самозаписи (FR-ENROL-02), по порядку: выключена → self_enrol_disabled; срок истёк → self_enrol_closed;
 * неверный код → invalid_code; мест нет → course_full. Код сравнивается за постоянное время.
 * Курсы для учеников бесплатны — школа платит только подписку на платформу (ADR-012).
 */
public final class SelfEnrolPolicy {

    public static final String DISABLED = "enrollment.self_enrol_disabled";
    public static final String CLOSED = "enrollment.self_enrol_closed";
    public static final String INVALID_CODE = "enrollment.invalid_code";
    public static final String COURSE_FULL = "enrollment.course_full";

    private SelfEnrolPolicy() {
    }

    /** Снимок условий: настройки курса, введённый код, число активных студентов, текущее время. */
    public record Request(boolean enabled, String requiredCode, Integer maxStudents, Instant until,
                          String providedCode, int activeStudents, Instant now) {
    }

    public static void check(Request request) {
        if (!request.enabled()) {
            throw new BusinessRuleException(DISABLED, "Self-enrolment is disabled");
        }
        if (request.until() != null && !request.now().isBefore(request.until())) {
            throw new BusinessRuleException(CLOSED, "Self-enrolment period is over");
        }
        if (request.requiredCode() != null && !codesMatch(request.requiredCode(), request.providedCode())) {
            throw new BusinessRuleException(INVALID_CODE, "Invalid enrolment key");
        }
        if (request.maxStudents() != null && request.activeStudents() >= request.maxStudents()) {
            throw new BusinessRuleException(COURSE_FULL, "No seats left");
        }
    }

    static boolean codesMatch(String expected, String provided) {
        if (provided == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), provided.trim().getBytes(StandardCharsets.UTF_8));
    }
}
