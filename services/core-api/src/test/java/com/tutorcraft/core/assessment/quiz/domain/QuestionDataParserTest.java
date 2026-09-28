package com.tutorcraft.core.assessment.quiz.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tutorcraft.core.shared.domain.FieldViolation;
import com.tutorcraft.core.shared.domain.ValidationException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Валидация QuestionInput по типу вопроса (FR-QBANK-03). */
class QuestionDataParserTest {

    private static Map<String, Object> option(String id, boolean correct) {
        return Map.of("id", id, "text", "Option " + id, "correct", correct);
    }

    private static List<String> codes(Runnable action) {
        try {
            action.run();
        } catch (ValidationException e) {
            return e.violations().stream().map(v -> v.field() + ":" + v.code()).toList();
        }
        throw new AssertionError("ValidationException expected");
    }

    @Test
    void singleChoiceRequiresExactlyOneCorrectOption() {
        Map<String, Object> twoCorrect = Map.of("options", List.of(option("a", true), option("b", true)));
        assertThat(codes(() -> QuestionDataParser.parse(QuestionType.SINGLE_CHOICE, twoCorrect)))
                .containsExactly("data.options:exactly_one_correct");
        QuestionData parsed = QuestionDataParser.parse(QuestionType.SINGLE_CHOICE,
                Map.of("options", List.of(option("a", false), option("b", true)), "shuffle", false));
        assertThat(parsed).isInstanceOf(QuestionData.SingleChoice.class);
        assertThat(((QuestionData.SingleChoice) parsed).shuffle()).isFalse();
    }

    @Test
    void choicesNeedAtLeastTwoUniqueNonBlankOptions() {
        Map<String, Object> data = Map.of("options", List.of(Map.of("id", "a", "text", " ", "correct", true)));
        assertThat(codes(() -> QuestionDataParser.parse(QuestionType.MULTIPLE_CHOICE, data)))
                .contains("data.options:too_few", "data.options[0].text:required");
        Map<String, Object> duplicate = Map.of("options", List.of(option("a", true), option("a", false)));
        assertThat(codes(() -> QuestionDataParser.parse(QuestionType.MULTIPLE_CHOICE, duplicate)))
                .containsExactly("data.options[1].id:duplicate");
    }

    @Test
    void multipleChoiceNeedsCorrectOptionAndKnownScoring() {
        Map<String, Object> noneCorrect = Map.of("options", List.of(option("a", false), option("b", false)), "scoring", "bogus");
        assertThat(codes(() -> QuestionDataParser.parse(QuestionType.MULTIPLE_CHOICE, noneCorrect)))
                .containsExactlyInAnyOrder("data.options:correct_required", "data.scoring:invalid");
        QuestionData.MultipleChoice parsed = (QuestionData.MultipleChoice) QuestionDataParser.parse(QuestionType.MULTIPLE_CHOICE,
                Map.of("options", List.of(option("a", true), option("b", false))));
        assertThat(parsed.scoring()).isEqualTo(MultipleChoiceScoring.PARTIAL);
    }

    @Test
    void trueFalseRequiresValue() {
        assertThat(codes(() -> QuestionDataParser.parse(QuestionType.TRUE_FALSE, Map.of())))
                .containsExactly("data.correct:required");
        assertThat(QuestionDataParser.parse(QuestionType.TRUE_FALSE, Map.of("correct", true)))
                .isEqualTo(new QuestionData.TrueFalse(true));
    }

    @Test
    void shortAnswerNeedsFullScoreAnswerAndValidPercents() {
        Map<String, Object> data = Map.of("answers", List.of(Map.of("pattern", "x", "scorePercent", 150),
                Map.of("pattern", "y", "scorePercent", 50)));
        assertThat(codes(() -> QuestionDataParser.parse(QuestionType.SHORT_ANSWER, data)))
                .contains("data.answers[0].scorePercent:out_of_range", "data.answers:full_score_required");
    }

    @Test
    void numericalRejectsNegativeToleranceAndMissingValue() {
        Map<String, Object> data = Map.of("answers", List.of(Map.of("tolerance", -1)));
        assertThat(codes(() -> QuestionDataParser.parse(QuestionType.NUMERICAL, data)))
                .contains("data.answers[0].value:required", "data.answers[0].tolerance:negative");
    }

    @Test
    void essayChecksWordLimits() {
        Map<String, Object> data = Map.of("responseFormat", "text", "minWords", 100, "maxWords", 10);
        assertThat(codes(() -> QuestionDataParser.parse(QuestionType.ESSAY, data))).containsExactly("data.maxWords:less_than_min");
        assertThat(codes(() -> QuestionDataParser.parse(QuestionType.ESSAY, Map.of("responseFormat", "video"))))
                .containsExactly("data.responseFormat:invalid");
    }

    @Test
    void matchingAndOrderingNeedTwoEntries() {
        assertThat(codes(() -> QuestionDataParser.parse(QuestionType.MATCHING,
                Map.of("pairs", List.of(Map.of("id", "p", "prompt", "a", "answer", "b"))))))
                .containsExactly("data.pairs:too_few");
        assertThat(codes(() -> QuestionDataParser.parse(QuestionType.ORDERING,
                Map.of("items", List.of(Map.of("id", "1", "text", "a"), Map.of("id", "1", "text", "b"))))))
                .containsExactly("data.items[1].id:duplicate");
    }

    @Test
    void wrongJsonTypeIsReportedWithFieldPath() {
        assertThatThrownBy(() -> QuestionDataParser.parse(QuestionType.SINGLE_CHOICE, Map.of("options", "nope")))
                .isInstanceOf(ValidationException.class)
                .extracting(e -> ((ValidationException) e).violations().get(0))
                .extracting(FieldViolation::code).isEqualTo("invalid_type");
    }

    @Test
    void draftRejectsMismatchedTypeAndBadScore() {
        Map<String, Object> data = Map.of("type", "true_false", "correct", true);
        assertThat(codes(() -> QuestionDraft.of("single_choice", "Title", Map.of(), BigDecimal.ZERO, null, List.of(), data, null)))
                .contains("defaultScore:out_of_range", "data.type:type_mismatch");
        QuestionDraft draft = QuestionDraft.of("true_false", " Title ", Map.of(), BigDecimal.ONE, null,
                List.of(" tag ", "tag", ""), data, null);
        assertThat(draft.title()).isEqualTo("Title");
        assertThat(draft.tags()).containsExactly("tag");
    }

    @Test
    void storedDataRoundTripsThroughMap() {
        QuestionData original = QuestionDataParser.parse(QuestionType.MATCHING, Map.of("pairs", List.of(
                Map.of("id", "p1", "prompt", "a", "answer", "1"), Map.of("id", "p2", "prompt", "b", "answer", "2"))));
        assertThat(QuestionDataParser.parse(QuestionType.MATCHING, original.toMap())).isEqualTo(original);
    }
}
