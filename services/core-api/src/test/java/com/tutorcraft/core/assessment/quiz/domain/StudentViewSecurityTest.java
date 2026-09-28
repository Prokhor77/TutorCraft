package com.tutorcraft.core.assessment.quiz.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutorcraft.core.assessment.quiz.application.QuizViews.AttemptView;
import com.tutorcraft.core.assessment.quiz.application.QuizViews.StudentQuestionView;
import com.tutorcraft.core.assessment.quiz.domain.QuestionData.ChoiceOption;
import com.tutorcraft.core.assessment.quiz.domain.QuestionData.MatchPair;
import com.tutorcraft.core.assessment.quiz.domain.QuestionData.NumericAnswer;
import com.tutorcraft.core.assessment.quiz.domain.QuestionData.OrderItem;
import com.tutorcraft.core.assessment.quiz.domain.QuestionData.PatternAnswer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** NFR-SEC-08: сериализованное представление вопросов для студента не содержит ключей ответов ни для одного типа. */
class StudentViewSecurityTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final UUID ATTEMPT = UUID.fromString("0192f3c1-0000-7000-8000-000000000001");
    private static final String SECRET_FEEDBACK = "SECRET-FEEDBACK";
    private static final String SECRET_PATTERN = "SECRET-PATTERN";

    private static List<QuestionData> allTypes() {
        return List.of(
                new QuestionData.SingleChoice(List.of(new ChoiceOption("a", "A", true, SECRET_FEEDBACK),
                        new ChoiceOption("b", "B", false, SECRET_FEEDBACK)), true),
                new QuestionData.MultipleChoice(List.of(new ChoiceOption("a", "A", true, SECRET_FEEDBACK),
                        new ChoiceOption("b", "B", false, null)), true, MultipleChoiceScoring.PARTIAL),
                new QuestionData.TrueFalse(true),
                new QuestionData.ShortAnswer(List.of(new PatternAnswer(SECRET_PATTERN, 100)), false),
                new QuestionData.Numerical(List.of(new NumericAnswer(42.4242, 0.5, 100))),
                new QuestionData.Essay(EssayFormat.TEXT_AND_FILES, 10, 100),
                new QuestionData.Matching(List.of(new MatchPair("p1", "Russia", "Moscow"), new MatchPair("p2", "France", "Paris"),
                        new MatchPair("p3", "Italy", "Rome")), true),
                new QuestionData.Ordering(List.of(new OrderItem("1", "one"), new OrderItem("2", "two"), new OrderItem("3", "three"))));
    }

    private static String serializedAttempt() throws Exception {
        List<StudentQuestionView> questions = new ArrayList<>();
        int slot = 1;
        for (QuestionData data : allTypes()) {
            List<String> order = OptionOrder.forQuestion(data, true, SeededShuffle.seed(ATTEMPT, slot));
            PublicQuestionParts parts = PublicQuestionParts.of(data, order);
            questions.add(new StudentQuestionView(slot, 1, BigDecimal.ONE, data.type().key(), "Q" + slot,
                    Map.of("schemaVersion", 1, "blocks", List.of()), parts.options(), parts.prompts(), parts.answerChoices(),
                    parts.items(), parts.responseFormat(), null, false));
            slot++;
        }
        AttemptView view = new AttemptView(ATTEMPT, UUID.randomUUID(), 1, "in_progress", Instant.EPOCH, null, Instant.EPOCH,
                questions, 1);
        return MAPPER.writeValueAsString(view);
    }

    @Test
    void serializedStudentViewHasNoAnswerKeys() throws Exception {
        String json = serializedAttempt();
        assertThat(json).doesNotContain("\"correct\"", "correctResponse", "\"feedback\"", "\"scorePercent\"",
                "\"tolerance\"", "\"pattern\"", "\"answer\"", "\"pairs\"", "\"value\"");
        assertThat(json).doesNotContain(SECRET_FEEDBACK, SECRET_PATTERN, "42.4242");
    }

    @Test
    void matchingAnswersAreDetachedFromPromptsAndShuffled() {
        QuestionData.Matching matching = (QuestionData.Matching) allTypes().get(6);
        PublicQuestionParts parts = PublicQuestionParts.of(matching,
                OptionOrder.forQuestion(matching, false, SeededShuffle.seed(ATTEMPT, 7)));
        assertThat(parts.prompts()).extracting(PublicQuestionParts.Labeled::text).containsExactly("Russia", "France", "Italy");
        assertThat(parts.answerChoices()).containsExactlyInAnyOrder("Moscow", "Paris", "Rome")
                .isNotEqualTo(List.of("Moscow", "Paris", "Rome"));
    }

    @Test
    void orderingItemsNeverArriveInCorrectOrderAndStayStableForTheSameAttempt() {
        QuestionData.Ordering ordering = (QuestionData.Ordering) allTypes().get(7);
        List<String> first = OptionOrder.forQuestion(ordering, false, SeededShuffle.seed(ATTEMPT, 8));
        List<String> reload = OptionOrder.forQuestion(ordering, false, SeededShuffle.seed(ATTEMPT, 8));
        assertThat(first).isEqualTo(reload).isNotEqualTo(List.of("1", "2", "3")).containsExactlyInAnyOrder("1", "2", "3");
    }

    @Test
    void choiceOptionsKeepAuthorOrderWhenShufflingDisabled() {
        QuestionData single = allTypes().get(0);
        assertThat(OptionOrder.forQuestion(single, false, 1L)).containsExactly("a", "b");
        assertThat(OptionOrder.forQuestion(new QuestionData.TrueFalse(true), true, 1L)).isEmpty();
    }
}
