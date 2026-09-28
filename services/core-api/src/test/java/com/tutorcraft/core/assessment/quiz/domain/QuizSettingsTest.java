package com.tutorcraft.core.assessment.quiz.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tutorcraft.core.shared.domain.ValidationException;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Настройки теста: умолчания (UX-02), валидация (DATA-04), правила показа (FR-QUIZ-05). */
class QuizSettingsTest {

    @Test
    void defaultsMatchContract() {
        QuizSettings settings = QuizSettings.parse(Map.of());
        assertThat(settings.timeLimitSec()).isNull();
        assertThat(settings.maxAttempts()).isNull();
        assertThat(settings.gradingMethod()).isEqualTo(GradingMethod.HIGHEST);
        assertThat(settings.questionsPerPage()).isEqualTo(5);
        assertThat(settings.maxScore()).isEqualByComparingTo("10");
        assertThat(settings.review().whenScore()).isEqualTo(ReviewTiming.IMMEDIATELY);
        assertThat(settings.review().whenCorrectAnswers()).isEqualTo(ReviewTiming.AFTER_CLOSE);
        assertThat(QuizSettings.defaults().toMap()).containsEntry("kind", "quiz");
    }

    @Test
    void normalizedMapRoundTrips() {
        QuizSettings parsed = QuizSettings.parse(Map.of("openAt", "2026-10-01T09:00:00Z", "closeAt",
                Date.from(Instant.parse("2026-10-02T09:00:00Z")), "timeLimitSec", 1200, "maxAttempts", 3,
                "gradingMethod", "average", "passPercent", 60, "review", Map.of("whenCorrectAnswers", "never")));
        assertThat(QuizSettings.parse(parsed.toMap())).isEqualTo(parsed);
        assertThat(parsed.review().whenCorrectAnswers()).isEqualTo(ReviewTiming.NEVER);
    }

    @Test
    void rejectsInvalidValues() {
        assertThatThrownBy(() -> QuizSettings.parse(Map.of("timeLimitSec", 10, "openAt", "2026-10-02T00:00:00Z",
                "closeAt", "2026-10-01T00:00:00Z", "maxScore", 0)))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).violations())
                        .extracting(v -> v.field()).containsExactlyInAnyOrder("settings.timeLimitSec", "settings.maxScore",
                                "settings.closeAt"));
        assertThatThrownBy(() -> QuizSettings.parse(Map.of("gradingMethod", "median"))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> QuizSettings.parse(Map.of("review", Map.of("whenScore", "soon"))))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void reviewTimingRules() {
        Instant close = Instant.parse("2026-10-01T00:00:00Z");
        assertThat(ReviewTiming.IMMEDIATELY.allows(null, close)).isTrue();
        assertThat(ReviewTiming.AFTER_CLOSE.allows(close, close.minusSeconds(1))).isFalse();
        assertThat(ReviewTiming.AFTER_CLOSE.allows(close, close)).isTrue();
        assertThat(ReviewTiming.AFTER_CLOSE.allows(null, close)).isFalse();
        assertThat(ReviewTiming.NEVER.allows(close, close.plusSeconds(1))).isFalse();
    }
}
