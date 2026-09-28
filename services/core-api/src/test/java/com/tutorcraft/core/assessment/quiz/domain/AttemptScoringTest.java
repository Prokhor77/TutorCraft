package com.tutorcraft.core.assessment.quiz.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.tutorcraft.core.assessment.quiz.domain.AttemptScoring.AttemptScore;
import com.tutorcraft.core.assessment.quiz.domain.AttemptScoring.SlotScore;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class AttemptScoringTest {

    @Test
    void scalesRawPointsToQuizMaximum() {
        AttemptScore score = AttemptScoring.total(List.of(new SlotScore(new BigDecimal("2"), new BigDecimal("2.00")),
                new SlotScore(new BigDecimal("2"), new BigDecimal("1.00"))), BigDecimal.TEN);
        assertThat(score.score()).isEqualByComparingTo("7.50");
        assertThat(score.needsManualGrading()).isFalse();
    }

    @Test
    void pendingEssayCountsAsZeroAndFlagsManualGrading() {
        AttemptScore score = AttemptScoring.total(List.of(new SlotScore(BigDecimal.ONE, BigDecimal.ONE),
                new SlotScore(BigDecimal.ONE, null)), BigDecimal.TEN);
        assertThat(score.score()).isEqualByComparingTo("5.00");
        assertThat(score.needsManualGrading()).isTrue();
    }

    @Test
    void zeroPointQuizScoresZero() {
        assertThat(AttemptScoring.total(List.of(), BigDecimal.TEN).score()).isEqualByComparingTo("0");
    }

    @Test
    void slotScoreAndPercent() {
        assertThat(AttemptScoring.slotScore(new BigDecimal("3"), 1.0 / 3)).isEqualByComparingTo("1.00");
        assertThat(AttemptScoring.percent(new BigDecimal("5"), BigDecimal.TEN)).isEqualTo(50.0);
        assertThat(AttemptScoring.percent(null, BigDecimal.TEN)).isNull();
        assertThat(AttemptScoring.percent(BigDecimal.ONE, BigDecimal.ZERO)).isNull();
    }
}
