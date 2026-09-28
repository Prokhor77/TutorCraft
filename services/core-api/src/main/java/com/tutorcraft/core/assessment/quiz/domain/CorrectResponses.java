package com.tutorcraft.core.assessment.quiz.domain;

import com.tutorcraft.core.assessment.quiz.domain.QuestionData.ChoiceOption;
import com.tutorcraft.core.assessment.quiz.domain.QuestionData.NumericAnswer;
import com.tutorcraft.core.assessment.quiz.domain.QuestionData.OrderItem;
import com.tutorcraft.core.assessment.quiz.domain.QuestionData.PatternAnswer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Эталонный ответ для показа после попытки (FR-QUIZ-05). Отдаётся только когда разрешают правила показа.
 * Для эссе эталона нет.
 */
public final class CorrectResponses {

    private static final double FULL_PERCENT = 100.0;

    private CorrectResponses() {
    }

    public static Optional<QuestionResponse> of(QuestionData data) {
        return switch (data) {
            case QuestionData.SingleChoice single -> single.options().stream().filter(ChoiceOption::correct).findFirst()
                    .map(option -> new QuestionResponse.SelectedOption(option.id()));
            case QuestionData.MultipleChoice multiple -> Optional.of(new QuestionResponse.SelectedOptions(
                    multiple.options().stream().filter(ChoiceOption::correct).map(ChoiceOption::id).toList()));
            case QuestionData.TrueFalse trueFalse -> Optional.of(new QuestionResponse.BooleanValue(trueFalse.correct()));
            case QuestionData.ShortAnswer shortAnswer -> shortAnswer.answers().stream()
                    .filter(answer -> answer.scorePercent() == FULL_PERCENT).findFirst()
                    .map(PatternAnswer::pattern).map(QuestionResponse.Text::new);
            case QuestionData.Numerical numerical -> numerical.answers().stream()
                    .filter(answer -> answer.scorePercent() == FULL_PERCENT).findFirst()
                    .map(NumericAnswer::value).map(QuestionResponse.NumberValue::new);
            case QuestionData.Essay essay -> Optional.empty();
            case QuestionData.Matching matching -> Optional.of(new QuestionResponse.Matches(matches(matching)));
            case QuestionData.Ordering ordering -> Optional.of(new QuestionResponse.Order(
                    ordering.items().stream().map(OrderItem::id).toList()));
        };
    }

    private static Map<String, String> matches(QuestionData.Matching matching) {
        Map<String, String> result = new LinkedHashMap<>();
        matching.pairs().forEach(pair -> result.put(pair.id(), pair.answer()));
        return result;
    }
}
