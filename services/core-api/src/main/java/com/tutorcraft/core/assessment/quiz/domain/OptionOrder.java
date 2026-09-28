package com.tutorcraft.core.assessment.quiz.domain;

import com.tutorcraft.core.assessment.quiz.domain.QuestionData.ChoiceOption;
import com.tutorcraft.core.assessment.quiz.domain.QuestionData.MatchPair;
import com.tutorcraft.core.assessment.quiz.domain.QuestionData.OrderItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Порядок показа вариантов в попытке (снимок {@code optionOrder} слота):
 * <ul>
 *   <li>выбор — id вариантов, перемешанных, если перемешивание включено и в вопросе, и в тесте;</li>
 *   <li>matching — id пар в порядке показа вариантов ответа (всегда перемешан, иначе ответы совпали бы с подсказками);</li>
 *   <li>ordering — id элементов, всегда перемешанных и отличных от правильного порядка.</li>
 * </ul>
 */
public final class OptionOrder {

    private OptionOrder() {
    }

    public static List<String> forQuestion(QuestionData data, boolean shuffleAnswers, long seed) {
        return switch (data) {
            case QuestionData.SingleChoice single -> choiceOrder(ids(single.options(), ChoiceOption::id), single.shuffle() && shuffleAnswers, seed);
            case QuestionData.MultipleChoice multiple ->
                    choiceOrder(ids(multiple.options(), ChoiceOption::id), multiple.shuffle() && shuffleAnswers, seed);
            case QuestionData.Matching matching -> SeededShuffle.derange(ids(matching.pairs(), MatchPair::id), seed);
            case QuestionData.Ordering ordering -> SeededShuffle.derange(ids(ordering.items(), OrderItem::id), seed);
            default -> List.of();
        };
    }

    /**
     * Упорядочивает элементы по сохранённому порядку; элементы, которых нет в порядке (новая версия вопроса после
     * переоценки), идут в конце в исходном порядке.
     */
    public static <T> List<T> apply(List<T> items, Function<T, String> id, List<String> order) {
        Map<String, Integer> position = IntStream.range(0, order.size()).boxed()
                .collect(Collectors.toMap(order::get, Function.identity(), (first, second) -> first));
        List<T> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparingInt(item -> position.getOrDefault(id.apply(item), Integer.MAX_VALUE)));
        return List.copyOf(sorted);
    }

    private static List<String> choiceOrder(List<String> ids, boolean shuffle, long seed) {
        return shuffle ? SeededShuffle.shuffle(ids, seed) : ids;
    }

    private static <T> List<String> ids(List<T> items, Function<T, String> id) {
        return items.stream().map(id).toList();
    }
}
