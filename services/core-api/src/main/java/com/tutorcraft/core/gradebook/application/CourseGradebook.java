package com.tutorcraft.core.gradebook.application;

import com.tutorcraft.core.gradebook.application.GradebookStructureRepository.GradebookSettings;
import com.tutorcraft.core.gradebook.domain.FinalGradeCalculator;
import com.tutorcraft.core.gradebook.domain.GradeCategory;
import com.tutorcraft.core.gradebook.domain.GradeColumn;
import com.tutorcraft.core.gradebook.domain.LetterGrade;
import com.tutorcraft.core.gradebook.domain.ScaleLevel;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Снимок структуры журнала курса для расчёта итога и словесной оценки. */
record CourseGradebook(GradebookSettings settings, List<GradeCategory> categories, List<GradeColumn> columns,
                       List<ScaleLevel> scale) {

    Optional<BigDecimal> finalPercent(Map<UUID, BigDecimal> scores) {
        return FinalGradeCalculator.finalPercent(settings.aggregation(), categories, columns, scores);
    }

    String label(BigDecimal percent) {
        return LetterGrade.label(scale, percent).orElse(null);
    }

    List<UUID> columnIds() {
        return columns.stream().map(GradeColumn::id).toList();
    }
}
