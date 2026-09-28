package com.tutorcraft.core.assessment.quiz.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Метод итоговой оценки по завершённым попыткам (FR-QUIZ-01). */
public enum GradingMethod {
    HIGHEST("highest"), LAST("last"), AVERAGE("average"), FIRST("first");

    private static final int SCALE = 2;

    private final String key;

    GradingMethod(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    /**
     * @param attempts завершённые попытки (порядок не важен — сортируются по номеру)
     * @return итоговый балл; пусто, если попыток нет
     */
    public Optional<BigDecimal> aggregate(List<ScoredAttempt> attempts) {
        if (attempts.isEmpty()) {
            return Optional.empty();
        }
        List<ScoredAttempt> ordered = attempts.stream().sorted(Comparator.comparingInt(ScoredAttempt::number)).toList();
        return Optional.of(switch (this) {
            case HIGHEST -> ordered.stream().map(ScoredAttempt::score).max(Comparator.naturalOrder()).orElseThrow();
            case LAST -> ordered.get(ordered.size() - 1).score();
            case FIRST -> ordered.get(0).score();
            case AVERAGE -> ordered.stream().map(ScoredAttempt::score).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(ordered.size()), SCALE, RoundingMode.HALF_UP);
        });
    }

    public static Optional<GradingMethod> find(String key) {
        return Arrays.stream(values()).filter(value -> value.key.equals(key)).findFirst();
    }

    public record ScoredAttempt(int number, BigDecimal score) {
    }
}
