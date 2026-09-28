package com.tutorcraft.core.assessment.quiz.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.tutorcraft.core.assessment.quiz.domain.QuestionData.ChoiceOption;
import com.tutorcraft.core.assessment.quiz.domain.QuestionData.MatchPair;
import com.tutorcraft.core.assessment.quiz.domain.QuestionData.NumericAnswer;
import com.tutorcraft.core.assessment.quiz.domain.QuestionData.OrderItem;
import com.tutorcraft.core.assessment.quiz.domain.QuestionData.PatternAnswer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Эталонный ответ всегда получает полный балл (кроме эссе — эталона нет). */
class CorrectResponsesTest {

    @Test
    void correctResponseGradesAsFullyCorrect() {
        List<QuestionData> questions = List.of(
                new QuestionData.SingleChoice(List.of(new ChoiceOption("a", "A", false, null), new ChoiceOption("b", "B", true, null)), true),
                new QuestionData.MultipleChoice(List.of(new ChoiceOption("a", "A", true, null), new ChoiceOption("b", "B", false, null)),
                        true, MultipleChoiceScoring.ALL_OR_NOTHING),
                new QuestionData.TrueFalse(false),
                new QuestionData.ShortAnswer(List.of(new PatternAnswer("half", 50), new PatternAnswer("full", 100)), false),
                new QuestionData.Numerical(List.of(new NumericAnswer(1, 0, 100))),
                new QuestionData.Matching(List.of(new MatchPair("p1", "x", "1"), new MatchPair("p2", "y", "2")), true),
                new QuestionData.Ordering(List.of(new OrderItem("1", "a"), new OrderItem("2", "b"))));
        questions.forEach(data -> assertThat(QuestionGrader.grade(data, CorrectResponses.of(data).orElseThrow()).isFullyCorrect())
                .as(data.type().key()).isTrue());
    }

    @Test
    void essayHasNoReferenceAnswer() {
        assertThat(CorrectResponses.of(new QuestionData.Essay(EssayFormat.TEXT, null, null))).isEmpty();
    }

    @Test
    void responseShapesParseByKey() {
        assertThat(QuestionResponse.parse(Map.of("optionId", "a"))).isEqualTo(new QuestionResponse.SelectedOption("a"));
        assertThat(QuestionResponse.parse(Map.of("number", 2.5))).isEqualTo(new QuestionResponse.NumberValue(2.5));
        assertThat(QuestionResponse.parse(Map.of("order", List.of("1", "2")))).isEqualTo(new QuestionResponse.Order(List.of("1", "2")));
        assertThat(QuestionResponse.parse(Map.of("matches", Map.of("p1", "1"))))
                .isEqualTo(new QuestionResponse.Matches(Map.of("p1", "1")));
    }
}
