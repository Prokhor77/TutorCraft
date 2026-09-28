package com.tutorcraft.core.assessment.quiz.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.tutorcraft.core.assessment.quiz.domain.QuestionData.ChoiceOption;
import com.tutorcraft.core.assessment.quiz.domain.QuestionData.MatchPair;
import com.tutorcraft.core.assessment.quiz.domain.QuestionData.NumericAnswer;
import com.tutorcraft.core.assessment.quiz.domain.QuestionData.OrderItem;
import com.tutorcraft.core.assessment.quiz.domain.QuestionData.PatternAnswer;
import com.tutorcraft.core.assessment.quiz.domain.QuestionResponse.BooleanValue;
import com.tutorcraft.core.assessment.quiz.domain.QuestionResponse.EssayText;
import com.tutorcraft.core.assessment.quiz.domain.QuestionResponse.Matches;
import com.tutorcraft.core.assessment.quiz.domain.QuestionResponse.NumberValue;
import com.tutorcraft.core.assessment.quiz.domain.QuestionResponse.Order;
import com.tutorcraft.core.assessment.quiz.domain.QuestionResponse.SelectedOption;
import com.tutorcraft.core.assessment.quiz.domain.QuestionResponse.SelectedOptions;
import com.tutorcraft.core.assessment.quiz.domain.QuestionResponse.Text;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Автопроверка всех 8 типов (FR-QBANK-03, FR-QUIZ-04): полное покрытие ветвей QuestionGrader. */
class QuestionGraderTest {

    private static final double EPS = 1e-9;
    private static final QuestionResponse WRONG_SHAPE = new BooleanValue(true);

    private static List<ChoiceOption> options(boolean... correct) {
        String[] ids = {"a", "b", "c", "d", "e"};
        return java.util.stream.IntStream.range(0, correct.length)
                .mapToObj(i -> new ChoiceOption(ids[i], "Option " + ids[i], correct[i], null)).toList();
    }

    private static double fraction(QuestionData data, QuestionResponse response) {
        GradeOutcome outcome = QuestionGrader.grade(data, response);
        assertThat(outcome.manual()).isFalse();
        return outcome.fraction();
    }

    @Test
    void missingResponseIsZeroForEveryType() {
        assertThat(fraction(new QuestionData.TrueFalse(true), null)).isZero();
        assertThat(fraction(new QuestionData.Essay(EssayFormat.TEXT, null, null), null)).isZero();
    }

    @Nested
    class SingleChoice {

        private final QuestionData data = new QuestionData.SingleChoice(options(false, true, false), true);

        @Test
        void correctOptionGetsFullScore() {
            assertThat(fraction(data, new SelectedOption("b"))).isEqualTo(1.0);
        }

        @Test
        void wrongOptionGetsZero() {
            assertThat(fraction(data, new SelectedOption("a"))).isZero();
        }

        @Test
        void responseOfAnotherShapeGetsZero() {
            assertThat(fraction(data, WRONG_SHAPE)).isZero();
        }
    }

    @Nested
    class MultipleChoice {

        private QuestionData data(MultipleChoiceScoring scoring) {
            return new QuestionData.MultipleChoice(options(true, true, false, false), true, scoring);
        }

        @Test
        void allOrNothingRequiresExactlyTheCorrectSet() {
            QuestionData data = data(MultipleChoiceScoring.ALL_OR_NOTHING);
            assertThat(fraction(data, new SelectedOptions(List.of("a", "b")))).isEqualTo(1.0);
            assertThat(fraction(data, new SelectedOptions(List.of("a")))).isZero();
            assertThat(fraction(data, new SelectedOptions(List.of("a", "b", "c")))).isZero();
        }

        @Test
        void partialSubtractsShareOfWrongOptions() {
            QuestionData data = data(MultipleChoiceScoring.PARTIAL);
            assertThat(fraction(data, new SelectedOptions(List.of("a")))).isCloseTo(0.5, within(EPS));
            assertThat(fraction(data, new SelectedOptions(List.of("a", "b", "c")))).isCloseTo(0.5, within(EPS));
            assertThat(fraction(data, new SelectedOptions(List.of("a", "b", "c", "d")))).isZero();
        }

        @Test
        void partialWithoutWrongOptionsIgnoresPenaltyTerm() {
            QuestionData data = new QuestionData.MultipleChoice(options(true, true), true, MultipleChoiceScoring.PARTIAL);
            assertThat(fraction(data, new SelectedOptions(List.of("b")))).isCloseTo(0.5, within(EPS));
        }

        @Test
        void partialWithPenaltySubtractsWrongFromRightAndFloorsAtZero() {
            QuestionData data = data(MultipleChoiceScoring.PARTIAL_WITH_PENALTY);
            assertThat(fraction(data, new SelectedOptions(List.of("a", "b")))).isEqualTo(1.0);
            assertThat(fraction(data, new SelectedOptions(List.of("a", "b", "c")))).isCloseTo(0.5, within(EPS));
            assertThat(fraction(data, new SelectedOptions(List.of("c", "d")))).isZero();
        }

        @Test
        void responseOfAnotherShapeGetsZero() {
            assertThat(fraction(data(MultipleChoiceScoring.PARTIAL), new SelectedOption("a"))).isZero();
        }
    }

    @Nested
    class TrueFalse {

        @Test
        void matchingValueIsCorrect() {
            assertThat(fraction(new QuestionData.TrueFalse(false), new BooleanValue(false))).isEqualTo(1.0);
            assertThat(fraction(new QuestionData.TrueFalse(false), new BooleanValue(true))).isZero();
        }

        @Test
        void responseOfAnotherShapeGetsZero() {
            assertThat(fraction(new QuestionData.TrueFalse(true), new Text("true"))).isZero();
        }
    }

    @Nested
    class ShortAnswer {

        private final QuestionData insensitive = new QuestionData.ShortAnswer(List.of(
                new PatternAnswer("Moscow", 100), new PatternAnswer("Mos*", 50)), false);

        @Test
        void bestMatchingPatternWinsCaseInsensitively() {
            assertThat(fraction(insensitive, new Text("  moscow "))).isEqualTo(1.0);
            assertThat(fraction(insensitive, new Text("Moskva"))).isEqualTo(0.5);
            assertThat(fraction(insensitive, new Text("Paris"))).isZero();
        }

        @Test
        void caseSensitiveComparisonDistinguishesCase() {
            QuestionData sensitive = new QuestionData.ShortAnswer(List.of(new PatternAnswer("H2O", 100)), true);
            assertThat(fraction(sensitive, new Text("H2O"))).isEqualTo(1.0);
            assertThat(fraction(sensitive, new Text("h2o"))).isZero();
        }

        @Test
        void responseOfAnotherShapeGetsZero() {
            assertThat(fraction(insensitive, new NumberValue(1))).isZero();
        }
    }

    @Nested
    class Numerical {

        private final QuestionData data = new QuestionData.Numerical(List.of(
                new NumericAnswer(3.14, 0.01, 100), new NumericAnswer(3, 0.5, 50)));

        @Test
        void valueWithinToleranceScoresBestAnswer() {
            assertThat(fraction(data, new NumberValue(3.145))).isEqualTo(1.0);
            assertThat(fraction(data, new NumberValue(2.8))).isEqualTo(0.5);
            assertThat(fraction(data, new NumberValue(10))).isZero();
        }

        @Test
        void responseOfAnotherShapeGetsZero() {
            assertThat(fraction(data, new Text("3.14"))).isZero();
        }
    }

    @Nested
    class Essay {

        private final QuestionData data = new QuestionData.Essay(EssayFormat.TEXT, null, null);

        @Test
        void essayResponseNeedsManualGrading() {
            GradeOutcome outcome = QuestionGrader.grade(data, new EssayText(Map.of("blocks", List.of()), List.of()));
            assertThat(outcome.manual()).isTrue();
            assertThat(outcome.fraction()).isNull();
            assertThat(outcome.isFullyCorrect()).isFalse();
        }

        @Test
        void responseOfAnotherShapeGetsZero() {
            assertThat(fraction(data, new Text("my essay"))).isZero();
        }
    }

    @Nested
    class Matching {

        private final QuestionData data = new QuestionData.Matching(List.of(new MatchPair("p1", "Russia", "Moscow"),
                new MatchPair("p2", "France", "Paris"), new MatchPair("p3", "Italy", "Rome"), new MatchPair("p4", "Spain", "Madrid")),
                true);

        @Test
        void partialCreditByCorrectPairs() {
            assertThat(fraction(data, new Matches(Map.of("p1", "Moscow", "p2", "Paris", "p3", "Madrid")))).isEqualTo(0.5);
            assertThat(fraction(data, new Matches(Map.of("p1", "Moscow", "p2", "Paris", "p3", "Rome", "p4", "Madrid"))))
                    .isEqualTo(1.0);
        }

        @Test
        void responseOfAnotherShapeGetsZero() {
            assertThat(fraction(data, new Order(List.of("p1")))).isZero();
        }
    }

    @Nested
    class Ordering {

        private final QuestionData data = new QuestionData.Ordering(List.of(new OrderItem("1", "one"), new OrderItem("2", "two"),
                new OrderItem("3", "three"), new OrderItem("4", "four")));

        @Test
        void partialCreditByItemsInCorrectPosition() {
            assertThat(fraction(data, new Order(List.of("1", "2", "3", "4")))).isEqualTo(1.0);
            assertThat(fraction(data, new Order(List.of("1", "3", "2", "4")))).isEqualTo(0.5);
            assertThat(fraction(data, new Order(List.of("4", "3", "2", "1")))).isZero();
        }

        @Test
        void shorterResponseCountsOnlyGivenPositions() {
            assertThat(fraction(data, new Order(List.of("1")))).isEqualTo(0.25);
        }

        @Test
        void responseOfAnotherShapeGetsZero() {
            assertThat(fraction(data, new Matches(Map.of()))).isZero();
        }
    }
}
