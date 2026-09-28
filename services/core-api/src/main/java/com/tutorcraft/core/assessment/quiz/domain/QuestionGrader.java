package com.tutorcraft.core.assessment.quiz.domain;

import com.tutorcraft.core.assessment.quiz.domain.QuestionData.OrderItem;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Автопроверка ответа (FR-QUIZ-04). Чистая функция: ответ другой формы или отсутствующий ответ → 0.
 * <ul>
 *   <li>multiple_choice: {@code all_or_nothing} — 1, только если выбраны ровно все верные;
 *       {@code partial} — верные/всего_верных − неверные/всего_неверных;
 *       {@code partial_with_penalty} — (верные − неверные)/всего_верных; оба ограничены снизу нулём;</li>
 *   <li>short_answer / numerical — лучший процент среди подходящих вариантов;</li>
 *   <li>matching — доля верно сопоставленных пар;</li>
 *   <li>ordering — доля элементов на своих позициях (частичный балл по позициям);</li>
 *   <li>essay — ручная проверка (пустой ответ — 0).</li>
 * </ul>
 */
public final class QuestionGrader {

    private static final double PERCENT = 100.0;

    private QuestionGrader() {
    }

    public static GradeOutcome grade(QuestionData data, QuestionResponse response) {
        if (response == null) {
            return GradeOutcome.zero();
        }
        return switch (data) {
            case QuestionData.SingleChoice single -> singleChoice(single, response);
            case QuestionData.MultipleChoice multiple -> multipleChoice(multiple, response);
            case QuestionData.TrueFalse trueFalse -> trueFalse(trueFalse, response);
            case QuestionData.ShortAnswer shortAnswer -> shortAnswer(shortAnswer, response);
            case QuestionData.Numerical numerical -> numerical(numerical, response);
            case QuestionData.Essay essay -> response instanceof QuestionResponse.EssayText
                    ? GradeOutcome.manualGrading() : GradeOutcome.zero();
            case QuestionData.Matching matching -> matching(matching, response);
            case QuestionData.Ordering ordering -> ordering(ordering, response);
        };
    }

    private static GradeOutcome singleChoice(QuestionData.SingleChoice data, QuestionResponse response) {
        if (!(response instanceof QuestionResponse.SelectedOption selected)) {
            return GradeOutcome.zero();
        }
        boolean correct = data.options().stream().anyMatch(o -> o.correct() && o.id().equals(selected.optionId()));
        return correct ? GradeOutcome.full() : GradeOutcome.zero();
    }

    private static GradeOutcome multipleChoice(QuestionData.MultipleChoice data, QuestionResponse response) {
        if (!(response instanceof QuestionResponse.SelectedOptions selected)) {
            return GradeOutcome.zero();
        }
        Set<String> chosen = new HashSet<>(selected.optionIds());
        Set<String> correctIds = data.correctIds();
        long totalWrong = data.options().stream().filter(o -> !o.correct()).count();
        long rightChosen = data.options().stream().filter(o -> o.correct() && chosen.contains(o.id())).count();
        long wrongChosen = data.options().stream().filter(o -> !o.correct() && chosen.contains(o.id())).count();
        return switch (data.scoring()) {
            case ALL_OR_NOTHING -> rightChosen == correctIds.size() && wrongChosen == 0 ? GradeOutcome.full() : GradeOutcome.zero();
            case PARTIAL -> GradeOutcome.of((double) rightChosen / correctIds.size()
                    - (totalWrong == 0 ? 0 : (double) wrongChosen / totalWrong));
            case PARTIAL_WITH_PENALTY -> GradeOutcome.of((double) (rightChosen - wrongChosen) / correctIds.size());
        };
    }

    private static GradeOutcome trueFalse(QuestionData.TrueFalse data, QuestionResponse response) {
        boolean correct = response instanceof QuestionResponse.BooleanValue value && value.value() == data.correct();
        return correct ? GradeOutcome.full() : GradeOutcome.zero();
    }

    private static GradeOutcome shortAnswer(QuestionData.ShortAnswer data, QuestionResponse response) {
        if (!(response instanceof QuestionResponse.Text text)) {
            return GradeOutcome.zero();
        }
        double best = data.answers().stream()
                .filter(answer -> WildcardMatcher.matches(answer.pattern(), text.text(), data.caseSensitive()))
                .mapToDouble(QuestionData.PatternAnswer::scorePercent).max().orElse(0);
        return GradeOutcome.of(best / PERCENT);
    }

    private static GradeOutcome numerical(QuestionData.Numerical data, QuestionResponse response) {
        if (!(response instanceof QuestionResponse.NumberValue number)) {
            return GradeOutcome.zero();
        }
        double best = data.answers().stream()
                .filter(answer -> Math.abs(number.number() - answer.value()) <= answer.tolerance())
                .mapToDouble(QuestionData.NumericAnswer::scorePercent).max().orElse(0);
        return GradeOutcome.of(best / PERCENT);
    }

    private static GradeOutcome matching(QuestionData.Matching data, QuestionResponse response) {
        if (!(response instanceof QuestionResponse.Matches matches)) {
            return GradeOutcome.zero();
        }
        Map<String, String> chosen = matches.matches();
        long right = data.pairs().stream().filter(pair -> pair.answer().equals(chosen.get(pair.id()))).count();
        return GradeOutcome.of((double) right / data.pairs().size());
    }

    private static GradeOutcome ordering(QuestionData.Ordering data, QuestionResponse response) {
        if (!(response instanceof QuestionResponse.Order order)) {
            return GradeOutcome.zero();
        }
        List<OrderItem> items = data.items();
        List<String> given = order.order();
        long inPlace = 0;
        for (int i = 0; i < items.size() && i < given.size(); i++) {
            inPlace += items.get(i).id().equals(given.get(i)) ? 1 : 0;
        }
        return GradeOutcome.of((double) inPlace / items.size());
    }
}
