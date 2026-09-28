package com.tutorcraft.core.assessment.quiz.domain;

import com.tutorcraft.core.assessment.quiz.domain.QuestionData.ChoiceOption;
import com.tutorcraft.core.assessment.quiz.domain.QuestionData.MatchPair;
import com.tutorcraft.core.assessment.quiz.domain.QuestionData.OrderItem;
import java.util.List;

/**
 * «Публичные» части вопроса для студента (NFR-SEC-08): без флагов {@code correct}, отзывов вариантов,
 * шаблонов и значений ответов. Пары matching разделены на подсказки и перемешанные ответы,
 * элементы ordering перемешаны. Неприменимые к типу поля — null.
 */
public record PublicQuestionParts(List<Labeled> options, List<Labeled> prompts, List<String> answerChoices,
                                  List<Labeled> items, String responseFormat) {

    public record Labeled(String id, String text) {
    }

    private static final PublicQuestionParts EMPTY = new PublicQuestionParts(null, null, null, null, null);

    public static PublicQuestionParts of(QuestionData data, List<String> optionOrder) {
        return switch (data) {
            case QuestionData.SingleChoice single -> options(single.options(), optionOrder);
            case QuestionData.MultipleChoice multiple -> options(multiple.options(), optionOrder);
            case QuestionData.Essay essay -> new PublicQuestionParts(null, null, null, null, essay.responseFormat().key());
            case QuestionData.Matching matching -> matching(matching, optionOrder);
            case QuestionData.Ordering ordering -> new PublicQuestionParts(null, null, null,
                    OptionOrder.apply(ordering.items(), OrderItem::id, optionOrder).stream()
                            .map(item -> new Labeled(item.id(), item.text())).toList(), null);
            default -> EMPTY;
        };
    }

    private static PublicQuestionParts options(List<ChoiceOption> options, List<String> order) {
        List<Labeled> labeled = OptionOrder.apply(options, ChoiceOption::id, order).stream()
                .map(option -> new Labeled(option.id(), option.text())).toList();
        return new PublicQuestionParts(labeled, null, null, null, null);
    }

    private static PublicQuestionParts matching(QuestionData.Matching matching, List<String> order) {
        List<Labeled> prompts = matching.pairs().stream().map(pair -> new Labeled(pair.id(), pair.prompt())).toList();
        List<String> answers = OptionOrder.apply(matching.pairs(), MatchPair::id, order).stream()
                .map(MatchPair::answer).distinct().toList();
        return new PublicQuestionParts(null, prompts, answers, null, null);
    }
}
