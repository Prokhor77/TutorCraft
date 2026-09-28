package com.tutorcraft.core.assessment.quiz.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/** Подсчёт балла попытки: сумма баллов слотов, масштабированная к {@code maxScore} теста. */
public final class AttemptScoring {

    public static final int SCALE = 2;
    private static final int RATIO_SCALE = 10;
    private static final double PERCENT = 100.0;

    private AttemptScoring() {
    }

    /** Балл слота = баллы × доля (0..1), округление до сотых. */
    public static BigDecimal slotScore(BigDecimal points, double fraction) {
        return points.multiply(BigDecimal.valueOf(fraction)).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * @param answers баллы слотов; {@code score == null} — ждёт ручной проверки (считается 0 до проверки)
     */
    public static AttemptScore total(List<SlotScore> answers, BigDecimal maxScore) {
        BigDecimal raw = answers.stream().map(SlotScore::score).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal rawMax = answers.stream().map(SlotScore::points).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean pending = answers.stream().anyMatch(answer -> answer.score() == null);
        BigDecimal scaled = rawMax.signum() == 0 ? BigDecimal.ZERO.setScale(SCALE)
                : raw.divide(rawMax, RATIO_SCALE, RoundingMode.HALF_UP).multiply(maxScore).setScale(SCALE, RoundingMode.HALF_UP);
        return new AttemptScore(scaled, maxScore, pending);
    }

    /** Процент 0..100 (для «проходного балла»); null при нулевом максимуме. */
    public static Double percent(BigDecimal score, BigDecimal maxScore) {
        if (score == null || maxScore == null || maxScore.signum() == 0) {
            return null;
        }
        return score.doubleValue() * PERCENT / maxScore.doubleValue();
    }

    public record SlotScore(BigDecimal points, BigDecimal score) {
    }

    public record AttemptScore(BigDecimal score, BigDecimal maxScore, boolean needsManualGrading) {
    }
}
