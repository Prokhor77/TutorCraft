package com.tutorcraft.core.progress.domain;

import com.tutorcraft.core.progress.domain.Reason.Code;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Движок условий доступа (FR-PROG-04): чистая функция {@code evaluate(conditions, facts, now) → {available, reasons}}.
 * «all» — доступно, если выполнены все условия; «any» — хотя бы одно. Причины перечисляют невыполненные условия
 * и пусты для доступного элемента.
 */
public final class ConditionEvaluator {

    private ConditionEvaluator() {
    }

    public static Evaluation evaluate(ConditionGroup group, LearnerFacts facts, Instant now) {
        if (group == null || group.isEmpty()) {
            return Evaluation.OPEN;
        }
        List<Reason> unmet = group.conditions().stream()
                .map(condition -> check(condition, facts, now))
                .flatMap(Optional::stream)
                .toList();
        boolean available = switch (group.operator()) {
            case ALL -> unmet.isEmpty();
            case ANY -> unmet.size() < group.conditions().size();
        };
        return new Evaluation(available, group.showWhenLocked(), group.operator(), available ? List.of() : unmet);
    }

    /** @return причина, если условие не выполнено */
    static Optional<Reason> check(Condition condition, LearnerFacts facts, Instant now) {
        return switch (condition) {
            case Condition.DateWindow window -> dateWindow(window, now);
            case Condition.Completion completion -> facts.completedItems().contains(completion.itemId()) == completion.requireComplete()
                    ? Optional.empty()
                    : Optional.of(Reason.completion(completion.requireComplete() ? Code.COMPLETION_COMPLETE
                            : Code.COMPLETION_INCOMPLETE, completion.itemId()));
            case Condition.Grade grade -> grade(grade, facts.gradePercents().get(grade.itemId()));
            case Condition.Group group -> facts.groupIds().contains(group.groupId())
                    ? Optional.empty() : Optional.of(Reason.group(group.groupId()));
        };
    }

    private static Optional<Reason> dateWindow(Condition.DateWindow window, Instant now) {
        if (window.from() != null && now.isBefore(window.from())) {
            return Optional.of(Reason.date(Code.DATE_FROM, window.from()));
        }
        if (window.until() != null && !now.isBefore(window.until())) {
            return Optional.of(Reason.date(Code.DATE_UNTIL, window.until()));
        }
        return Optional.empty();
    }

    private static Optional<Reason> grade(Condition.Grade grade, Double current) {
        boolean aboveMin = grade.minPercent() == null || (current != null && current >= grade.minPercent());
        boolean belowMax = grade.maxPercent() == null || (current != null && current < grade.maxPercent());
        if (aboveMin && belowMax) {
            return Optional.empty();
        }
        return Optional.of(Reason.grade(gradeCode(grade), grade.itemId(), grade.minPercent(), grade.maxPercent(), current));
    }

    private static Code gradeCode(Condition.Grade grade) {
        if (grade.minPercent() != null && grade.maxPercent() != null) {
            return Code.GRADE_RANGE;
        }
        return grade.minPercent() != null ? Code.GRADE_MIN : Code.GRADE_MAX;
    }
}
