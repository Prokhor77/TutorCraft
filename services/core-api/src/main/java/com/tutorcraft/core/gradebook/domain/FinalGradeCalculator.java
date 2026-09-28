package com.tutorcraft.core.gradebook.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Итоговая оценка курса в процентах (FR-GRADE-02). Правила:
 * <ul>
 *   <li>учитываются только переданные оценки (для студента вызывающий код передаёт только опубликованные);
 *       элементы без оценки и с максимумом 0 исключаются;</li>
 *   <li>SUM, а также взвешенное среднее без категорий — сумма баллов / сумма максимумов оценённых элементов;</li>
 *   <li>WEIGHTED_MEAN — внутри категории сумма баллов / сумма максимумов, затем среднее по категориям с весами,
 *       нормированное на сумму весов категорий, где есть оценки; элементы без категории не участвуют.</li>
 * </ul>
 */
public final class FinalGradeCalculator {

    public static final int PERCENT_SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final MathContext PRECISION = MathContext.DECIMAL64;

    private FinalGradeCalculator() {
    }

    public static Optional<BigDecimal> finalPercent(Aggregation aggregation, List<GradeCategory> categories,
                                                    List<GradeColumn> columns, Map<UUID, BigDecimal> scores) {
        List<GradeColumn> graded = columns.stream()
                .filter(GradeColumn::hasPositiveMax)
                .filter(column -> scores.get(column.id()) != null)
                .toList();
        if (graded.isEmpty()) {
            return Optional.empty();
        }
        if (aggregation == Aggregation.SUM || categories.isEmpty()) {
            return Optional.of(round(pointsPercent(graded, scores)));
        }
        return weightedPercent(categories, graded, scores).map(FinalGradeCalculator::round);
    }

    private static Optional<BigDecimal> weightedPercent(List<GradeCategory> categories, List<GradeColumn> graded,
                                                        Map<UUID, BigDecimal> scores) {
        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal weights = BigDecimal.ZERO;
        for (GradeCategory category : categories) {
            List<GradeColumn> members = graded.stream().filter(column -> Objects.equals(column.categoryId(), category.id())).toList();
            if (members.isEmpty() || category.weight().signum() <= 0) {
                continue;
            }
            weightedSum = weightedSum.add(category.weight().multiply(pointsPercent(members, scores)));
            weights = weights.add(category.weight());
        }
        if (weights.signum() == 0) {
            return Optional.empty();
        }
        return Optional.of(weightedSum.divide(weights, PRECISION));
    }

    private static BigDecimal pointsPercent(List<GradeColumn> columns, Map<UUID, BigDecimal> scores) {
        BigDecimal earned = columns.stream().map(column -> scores.get(column.id())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal possible = columns.stream().map(GradeColumn::maxScore).reduce(BigDecimal.ZERO, BigDecimal::add);
        return earned.multiply(HUNDRED).divide(possible, PRECISION);
    }

    private static BigDecimal round(BigDecimal percent) {
        return percent.setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
    }
}
