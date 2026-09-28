package com.tutorcraft.core.progress.domain;

import com.tutorcraft.core.progress.domain.ConditionGroup.Operator;
import java.util.List;

/**
 * Результат вычисления условий: доступно ли и почему нет (FR-PROG-04). {@code operator} — как соединять причины
 * в тексте («и» для all, «или» для any).
 */
public record Evaluation(boolean available, boolean showWhenLocked, Operator operator, List<Reason> reasons) {

    public static final Evaluation OPEN = new Evaluation(true, true, Operator.ALL, List.of());

    public Evaluation {
        reasons = List.copyOf(reasons);
    }
}
