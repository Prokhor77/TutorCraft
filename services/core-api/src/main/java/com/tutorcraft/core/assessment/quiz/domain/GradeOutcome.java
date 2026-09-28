package com.tutorcraft.core.assessment.quiz.domain;

/**
 * Результат автопроверки: доля балла 0..1 или «требуется ручная проверка» (эссе).
 */
public record GradeOutcome(Double fraction, boolean manual) {

    public static final double FULL = 1.0;
    public static final double NONE = 0.0;

    public GradeOutcome {
        if (manual == (fraction != null)) {
            throw new IllegalArgumentException("Either fraction or manual must be set");
        }
        if (fraction != null && (fraction < NONE || fraction > FULL)) {
            throw new IllegalArgumentException("Fraction must be within 0..1");
        }
    }

    public static GradeOutcome of(double fraction) {
        return new GradeOutcome(Math.max(NONE, Math.min(FULL, fraction)), false);
    }

    public static GradeOutcome zero() {
        return of(NONE);
    }

    public static GradeOutcome full() {
        return of(FULL);
    }

    public static GradeOutcome manualGrading() {
        return new GradeOutcome(null, true);
    }

    public boolean isFullyCorrect() {
        return !manual && fraction == FULL;
    }
}
