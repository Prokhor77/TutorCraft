package com.tutorcraft.core.assessment.quiz.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Разворачивание состава теста в слоты попытки (FR-QUIZ-02, DATA-02). */
class AttemptLayoutBuilderTest {

    private static final UUID ATTEMPT = UUID.fromString("0192f3c1-0000-7000-8000-00000000abcd");

    private static QuestionCandidate candidate(int n) {
        UUID id = new UUID(0, n);
        return new QuestionCandidate(id, new UUID(1, n), BigDecimal.ONE, new QuestionData.TrueFalse(true));
    }

    private static QuizSettings settings(boolean shuffleQuestions, int perPage) {
        QuizSettings d = QuizSettings.defaults();
        return new QuizSettings(null, null, null, null, d.gradingMethod(), null, shuffleQuestions, true, perPage, d.maxScore(),
                d.review(), null);
    }

    @Test
    void fixedAndRandomSlotsExpandWithoutDuplicatesAndPinVersions() {
        QuestionCandidate fixed = candidate(1);
        List<QuestionCandidate> pool = IntStream.rangeClosed(1, 6).mapToObj(AttemptLayoutBuilderTest::candidate).toList();
        QuizLayout layout = QuizLayout.of(List.of(new LayoutSlot.Fixed(fixed.questionId(), new BigDecimal("2"), 1),
                new LayoutSlot.Random(null, "algebra", 3, null, 2)));
        List<AttemptSlot> slots = AttemptLayoutBuilder.build(ATTEMPT, layout, Map.of(fixed.questionId(), fixed),
                Map.of(1, pool), settings(false, 5));
        assertThat(slots).hasSize(4);
        assertThat(slots).extracting(AttemptSlot::slot).containsExactly(1, 2, 3, 4);
        assertThat(slots.get(0).points()).isEqualByComparingTo("2");
        assertThat(slots.get(0).questionVersionId()).isEqualTo(fixed.versionId());
        Set<UUID> questions = slots.stream().map(AttemptSlot::questionId).collect(Collectors.toSet());
        assertThat(questions).hasSize(4);
        assertThat(slots).extracting(AttemptSlot::page).containsExactly(1, 2, 2, 2);
    }

    @Test
    void drawIsDeterministicPerAttempt() {
        List<QuestionCandidate> pool = IntStream.rangeClosed(1, 10).mapToObj(AttemptLayoutBuilderTest::candidate).toList();
        QuizLayout layout = QuizLayout.of(List.of(new LayoutSlot.Random(UUID.randomUUID(), null, 3, null, null)));
        List<AttemptSlot> first = AttemptLayoutBuilder.build(ATTEMPT, layout, Map.of(), Map.of(0, pool), settings(true, 5));
        List<AttemptSlot> second = AttemptLayoutBuilder.build(ATTEMPT, layout, Map.of(), Map.of(0, pool.reversed()), settings(true, 5));
        assertThat(first).isEqualTo(second);
    }

    @Test
    void missingQuestionsAreSkippedAndPagesRenumbered() {
        QuestionCandidate kept = candidate(2);
        QuizLayout layout = QuizLayout.of(List.of(new LayoutSlot.Fixed(UUID.randomUUID(), null, 1),
                new LayoutSlot.Fixed(kept.questionId(), null, 4)));
        List<AttemptSlot> slots = AttemptLayoutBuilder.build(ATTEMPT, layout, Map.of(kept.questionId(), kept), Map.of(),
                settings(false, 5));
        assertThat(slots).singleElement().satisfies(slot -> {
            assertThat(slot.page()).isEqualTo(1);
            assertThat(slot.points()).isEqualByComparingTo("1");
        });
    }

    @Test
    void pagesFollowQuestionsPerPageWhenNotSpecified() {
        List<LayoutSlot> fixedSlots = IntStream.rangeClosed(1, 3)
                .mapToObj(n -> (LayoutSlot) new LayoutSlot.Fixed(candidate(n).questionId(), null, null)).toList();
        Map<UUID, QuestionCandidate> fixed = IntStream.rangeClosed(1, 3).mapToObj(AttemptLayoutBuilderTest::candidate)
                .collect(Collectors.toMap(QuestionCandidate::questionId, c -> c));
        List<AttemptSlot> slots = AttemptLayoutBuilder.build(ATTEMPT, QuizLayout.of(fixedSlots), fixed, Map.of(), settings(false, 2));
        assertThat(slots).extracting(AttemptSlot::page).containsExactly(1, 1, 2);
    }
}
