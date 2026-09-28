package com.tutorcraft.core.assessment.quiz.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.tutorcraft.core.assessment.quiz.domain.GradingMethod.ScoredAttempt;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Итоговая оценка по попыткам (FR-QUIZ-01). */
class GradingMethodTest {

    private static final List<ScoredAttempt> ATTEMPTS = List.of(new ScoredAttempt(2, new BigDecimal("9.00")),
            new ScoredAttempt(1, new BigDecimal("4.00")), new ScoredAttempt(3, new BigDecimal("6.50")));

    @Test
    void aggregatesByMethod() {
        assertThat(GradingMethod.HIGHEST.aggregate(ATTEMPTS)).contains(new BigDecimal("9.00"));
        assertThat(GradingMethod.FIRST.aggregate(ATTEMPTS)).contains(new BigDecimal("4.00"));
        assertThat(GradingMethod.LAST.aggregate(ATTEMPTS)).contains(new BigDecimal("6.50"));
        assertThat(GradingMethod.AVERAGE.aggregate(ATTEMPTS)).contains(new BigDecimal("6.50"));
    }

    @Test
    void averageIsRoundedHalfUpToHundredths() {
        List<ScoredAttempt> attempts = List.of(new ScoredAttempt(1, BigDecimal.ONE), new ScoredAttempt(2, BigDecimal.ZERO),
                new ScoredAttempt(3, BigDecimal.ZERO));
        assertThat(GradingMethod.AVERAGE.aggregate(attempts)).contains(new BigDecimal("0.33"));
    }

    @Test
    void noAttemptsNoGrade() {
        assertThat(GradingMethod.HIGHEST.aggregate(List.of())).isEmpty();
    }

    @Test
    void keysResolve() {
        assertThat(GradingMethod.find("average")).contains(GradingMethod.AVERAGE);
        assertThat(GradingMethod.find("median")).isEmpty();
    }
}
