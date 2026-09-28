package com.tutorcraft.core.gradebook.application;

import com.tutorcraft.core.gradebook.domain.Grade;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** Баллы для расчёта итога: столбец → действующий балл (только опубликованные — для студента, все — для преподавателя). */
final class PublishedScores {

    private PublishedScores() {
    }

    static Map<UUID, BigDecimal> of(List<Grade> grades) {
        return collect(grades, Grade::published);
    }

    static Map<UUID, BigDecimal> all(List<Grade> grades) {
        return collect(grades, grade -> true);
    }

    private static Map<UUID, BigDecimal> collect(List<Grade> grades, Predicate<Grade> filter) {
        Map<UUID, BigDecimal> scores = new HashMap<>();
        grades.stream()
                .filter(grade -> grade.finalScore() != null)
                .filter(filter)
                .forEach(grade -> scores.put(grade.columnId(), grade.finalScore()));
        return scores;
    }
}
