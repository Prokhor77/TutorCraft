package com.tutorcraft.core.assessment.quiz.application;

import com.tutorcraft.core.assessment.quiz.domain.BlockText;
import com.tutorcraft.core.assessment.quiz.domain.QuestionData;
import com.tutorcraft.core.assessment.quiz.domain.QuestionData.ChoiceOption;
import com.tutorcraft.core.assessment.quiz.domain.QuestionResponse;
import com.tutorcraft.core.assessment.quiz.domain.QuestionVersion;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Текст отзыва в результате: отзывы выбранных вариантов + общий отзыв вопроса. */
final class ResultFeedback {

    private static final String SEPARATOR = "\n";

    private ResultFeedback() {
    }

    static String of(QuestionVersion version, Map<String, Object> rawResponse) {
        List<String> parts = new ArrayList<>(optionFeedback(version.data(), rawResponse));
        String general = BlockText.of(version.generalFeedback());
        if (!general.isBlank()) {
            parts.add(general);
        }
        return parts.isEmpty() ? null : String.join(SEPARATOR, parts);
    }

    private static List<String> optionFeedback(QuestionData data, Map<String, Object> rawResponse) {
        if (rawResponse == null) {
            return List.of();
        }
        QuestionResponse response = QuestionResponse.parse(rawResponse);
        Set<String> selected = switch (response) {
            case QuestionResponse.SelectedOption one -> Set.of(one.optionId());
            case QuestionResponse.SelectedOptions many -> Set.copyOf(many.optionIds());
            default -> Set.of();
        };
        List<ChoiceOption> options = switch (data) {
            case QuestionData.SingleChoice single -> single.options();
            case QuestionData.MultipleChoice multiple -> multiple.options();
            default -> List.of();
        };
        return options.stream().filter(o -> selected.contains(o.id()) && o.feedback() != null && !o.feedback().isBlank())
                .map(ChoiceOption::feedback).toList();
    }
}
