package com.tutorcraft.core.assessment.quiz.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Окно, лимит времени и попытки с исключениями (FR-QUIZ-06); срок попытки (AC-4). */
class QuizRulesTest {

    private static final Instant NOW = Instant.parse("2026-09-27T10:00:00Z");
    private static final Instant CLOSE = Instant.parse("2026-09-27T12:00:00Z");
    private static final int TWENTY_MINUTES = 1200;

    private static QuizSettings settings(Instant openAt, Instant closeAt, Integer timeLimit, Integer maxAttempts) {
        QuizSettings defaults = QuizSettings.defaults();
        return new QuizSettings(openAt, closeAt, timeLimit, maxAttempts, defaults.gradingMethod(), null, false, true,
                defaults.questionsPerPage(), defaults.maxScore(), defaults.review(), null);
    }

    private static QuizOverride userOverride(Instant closeAt, Integer timeLimit, Integer maxAttempts) {
        return new QuizOverride(UUID.randomUUID(), UUID.randomUUID(), null, null, closeAt, timeLimit, maxAttempts);
    }

    private static QuizOverride groupOverride(Instant openAt, Instant closeAt, Integer timeLimit, Integer maxAttempts) {
        return new QuizOverride(UUID.randomUUID(), null, UUID.randomUUID(), openAt, closeAt, timeLimit, maxAttempts);
    }

    @Test
    void withoutOverridesSettingsApply() {
        QuizRules rules = QuizRules.effective(settings(null, CLOSE, TWENTY_MINUTES, 2), List.of());
        assertThat(rules).isEqualTo(new QuizRules(null, CLOSE, TWENTY_MINUTES, 2));
    }

    @Test
    void userOverrideWinsOverGroupOverrides() {
        QuizRules rules = QuizRules.effective(settings(null, CLOSE, TWENTY_MINUTES, 1),
                List.of(groupOverride(null, null, 3600, 5), userOverride(null, 1800, null)));
        assertThat(rules.timeLimitSec()).isEqualTo(1800);
        assertThat(rules.maxAttempts()).isEqualTo(5);
    }

    @Test
    void mostLenientGroupOverrideApplies() {
        Instant earlyOpen = NOW.minus(Duration.ofDays(2));
        Instant lateClose = CLOSE.plus(Duration.ofDays(1));
        QuizRules rules = QuizRules.effective(settings(NOW, CLOSE, TWENTY_MINUTES, 1), List.of(
                groupOverride(earlyOpen, CLOSE, 1500, 2), groupOverride(NOW.minus(Duration.ofDays(1)), lateClose, 2400, 3)));
        assertThat(rules).isEqualTo(new QuizRules(earlyOpen, lateClose, 2400, 3));
    }

    @Test
    void timeDueIsTheEarlierOfLimitAndClose() {
        assertThat(new QuizRules(null, CLOSE, TWENTY_MINUTES, null).timeDue(NOW)).isEqualTo(NOW.plusSeconds(TWENTY_MINUTES));
        assertThat(new QuizRules(null, NOW.plusSeconds(60), TWENTY_MINUTES, null).timeDue(NOW)).isEqualTo(NOW.plusSeconds(60));
        assertThat(new QuizRules(null, null, TWENTY_MINUTES, null).timeDue(NOW)).isEqualTo(NOW.plusSeconds(TWENTY_MINUTES));
        assertThat(new QuizRules(null, CLOSE, null, null).timeDue(NOW)).isEqualTo(CLOSE);
        assertThat(new QuizRules(null, null, null, null).timeDue(NOW)).isNull();
    }

    @Test
    void startIsDeniedOutsideWindowOrWithoutAttempts() {
        assertThat(new QuizRules(NOW.plusSeconds(1), null, null, null).startDenial(NOW, 0)).contains(QuizRules.NOT_OPEN);
        assertThat(new QuizRules(null, NOW, null, null).startDenial(NOW, 0)).contains(QuizRules.CLOSED);
        assertThat(new QuizRules(null, null, null, 2).startDenial(NOW, 2)).contains(QuizRules.NO_ATTEMPTS_LEFT);
        assertThat(new QuizRules(NOW, CLOSE, null, 2).startDenial(NOW, 1)).isEmpty();
    }

    @Test
    void answersAcceptedUntilDuePlusGrace() {
        Duration grace = Duration.ofSeconds(5);
        Instant due = NOW.plusSeconds(TWENTY_MINUTES);
        assertThat(QuizRules.acceptsAnswers(due, due.plusSeconds(5), grace)).isTrue();
        assertThat(QuizRules.acceptsAnswers(due, due.plusSeconds(6), grace)).isFalse();
        assertThat(QuizRules.acceptsAnswers(null, NOW, grace)).isTrue();
    }
}
