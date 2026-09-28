package com.tutorcraft.core.gradebook.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FinalGradeCalculatorTest {

    private static final UUID COURSE = UUID.randomUUID();
    private final GradeCategory assignments = category("Задания", 40, 0);
    private final GradeCategory quizzes = category("Тесты", 60, 1);

    @Test
    void noGradesGiveNoFinal() {
        GradeColumn column = column(assignments, 100);
        assertThat(FinalGradeCalculator.finalPercent(Aggregation.WEIGHTED_MEAN, List.of(assignments), List.of(column), Map.of()))
                .isEmpty();
    }

    @Test
    void sumAggregationUsesPointsOfGradedItemsOnly() {
        GradeColumn first = column(null, 100);
        GradeColumn second = column(null, 50);
        GradeColumn missing = column(null, 100);
        Map<UUID, BigDecimal> scores = Map.of(first.id(), dec(80), second.id(), dec(25));

        assertThat(FinalGradeCalculator.finalPercent(Aggregation.SUM, List.of(), List.of(first, second, missing), scores))
                .contains(new BigDecimal("70.00"));
    }

    @Test
    void weightedMeanWithoutCategoriesFallsBackToPoints() {
        GradeColumn first = column(null, 10);
        GradeColumn second = column(null, 30);
        Map<UUID, BigDecimal> scores = Map.of(first.id(), dec(10), second.id(), dec(10));

        assertThat(FinalGradeCalculator.finalPercent(Aggregation.WEIGHTED_MEAN, List.of(), List.of(first, second), scores))
                .contains(new BigDecimal("50.00"));
    }

    @Test
    void weightedMeanCombinesCategoryPercentsByWeight() {
        GradeColumn essay = column(assignments, 100);
        GradeColumn lab = column(assignments, 100);
        GradeColumn quiz = column(quizzes, 20);
        Map<UUID, BigDecimal> scores = Map.of(essay.id(), dec(100), lab.id(), dec(50), quiz.id(), dec(10));

        // Задания: 150/200 = 75%, Тесты: 10/20 = 50% → (40×75 + 60×50) / 100 = 60
        assertThat(FinalGradeCalculator.finalPercent(Aggregation.WEIGHTED_MEAN, List.of(assignments, quizzes),
                List.of(essay, lab, quiz), scores)).contains(new BigDecimal("60.00"));
    }

    @Test
    void categoriesWithoutGradesAreExcludedAndWeightsRenormalized() {
        GradeColumn essay = column(assignments, 100);
        GradeColumn quiz = column(quizzes, 100);
        Map<UUID, BigDecimal> scores = Map.of(essay.id(), dec(80));

        assertThat(FinalGradeCalculator.finalPercent(Aggregation.WEIGHTED_MEAN, List.of(assignments, quizzes),
                List.of(essay, quiz), scores)).contains(new BigDecimal("80.00"));
    }

    @Test
    void weightsNotAddingUpTo100AreNormalized() {
        GradeCategory tasks = category("Задания", 40, 0);
        GradeCategory tests = category("Тесты", 50, 1);
        GradeColumn essay = column(tasks, 100);
        GradeColumn quiz = column(tests, 100);
        Map<UUID, BigDecimal> scores = Map.of(essay.id(), dec(90), quiz.id(), dec(0));

        // (40×90 + 50×0) / 90 = 40
        assertThat(FinalGradeCalculator.finalPercent(Aggregation.WEIGHTED_MEAN, List.of(tasks, tests), List.of(essay, quiz), scores))
                .contains(new BigDecimal("40.00"));
    }

    @Test
    void zeroWeightCategoryAndUncategorizedItemsDoNotCount() {
        GradeCategory bonus = category("Бонус", 0, 2);
        GradeColumn essay = column(assignments, 100);
        GradeColumn extra = column(bonus, 100);
        GradeColumn loose = column(null, 100);
        Map<UUID, BigDecimal> scores = Map.of(essay.id(), dec(50), extra.id(), dec(100), loose.id(), dec(100));

        assertThat(FinalGradeCalculator.finalPercent(Aggregation.WEIGHTED_MEAN, List.of(assignments, bonus),
                List.of(essay, extra, loose), scores)).contains(new BigDecimal("50.00"));
    }

    @Test
    void onlyZeroWeightCategoriesGradedGivesNoFinal() {
        GradeCategory bonus = category("Бонус", 0, 0);
        GradeColumn extra = column(bonus, 100);

        assertThat(FinalGradeCalculator.finalPercent(Aggregation.WEIGHTED_MEAN, List.of(bonus), List.of(extra),
                Map.of(extra.id(), dec(100)))).isEmpty();
    }

    @Test
    void zeroMaxItemsAreIgnored() {
        GradeColumn zero = column(null, 0);
        GradeColumn real = column(null, 10);
        Map<UUID, BigDecimal> scores = new HashMap<>();
        scores.put(zero.id(), dec(5));
        scores.put(real.id(), dec(7));

        assertThat(FinalGradeCalculator.finalPercent(Aggregation.SUM, List.of(), List.of(zero, real), scores))
                .contains(new BigDecimal("70.00"));
        assertThat(FinalGradeCalculator.finalPercent(Aggregation.SUM, List.of(), List.of(zero), Map.of(zero.id(), dec(5))))
                .isEmpty();
    }

    @Test
    void resultIsRoundedHalfUpToTwoDecimals() {
        GradeColumn column = column(null, 3);

        assertThat(FinalGradeCalculator.finalPercent(Aggregation.SUM, List.of(), List.of(column), Map.of(column.id(), dec(2))))
                .contains(new BigDecimal("66.67"));
    }

    private static GradeCategory category(String name, int weight, int position) {
        return new GradeCategory(UUID.randomUUID(), name, BigDecimal.valueOf(weight), position);
    }

    private static GradeColumn column(GradeCategory category, int maxScore) {
        return new GradeColumn(UUID.randomUUID(), COURSE, UUID.randomUUID(), "item", BigDecimal.valueOf(maxScore),
                category == null ? null : category.id(), 0);
    }

    private static BigDecimal dec(int value) {
        return BigDecimal.valueOf(value);
    }
}
