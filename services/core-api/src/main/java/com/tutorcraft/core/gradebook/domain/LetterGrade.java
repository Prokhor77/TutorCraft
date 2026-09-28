package com.tutorcraft.core.gradebook.domain;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Перевод процента в словесную оценку по шкале (FR-GRADE-05): уровень с наибольшей границей, не превышающей процент. */
public final class LetterGrade {

    private LetterGrade() {
    }

    public static Optional<String> label(List<ScaleLevel> levels, BigDecimal percent) {
        if (percent == null || levels == null) {
            return Optional.empty();
        }
        return levels.stream()
                .filter(level -> level.minPercent().compareTo(percent) <= 0)
                .max(Comparator.comparing(ScaleLevel::minPercent))
                .map(ScaleLevel::name);
    }
}
