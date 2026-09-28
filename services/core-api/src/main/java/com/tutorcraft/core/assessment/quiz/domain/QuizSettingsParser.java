package com.tutorcraft.core.assessment.quiz.domain;

import com.tutorcraft.core.shared.domain.ValidationException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** Разбор полей QuizSettings из JSON-дерева с подстановкой умолчаний. */
final class QuizSettingsParser {

    private static final String PREFIX = "settings.";

    private QuizSettingsParser() {
    }

    static QuizSettings parse(Map<String, Object> raw) {
        QuizSettings defaults = QuizSettings.defaults();
        Integer perPage = MapValues.integer(raw, "questionsPerPage", PREFIX + "questionsPerPage");
        Double maxScore = MapValues.number(raw, "maxScore", PREFIX + "maxScore");
        return new QuizSettings(
                MapValues.instant(raw, "openAt", PREFIX + "openAt"),
                MapValues.instant(raw, "closeAt", PREFIX + "closeAt"),
                MapValues.integer(raw, "timeLimitSec", PREFIX + "timeLimitSec"),
                MapValues.integer(raw, "maxAttempts", PREFIX + "maxAttempts"),
                enumValue(raw, "gradingMethod", GradingMethod::find, defaults.gradingMethod()),
                MapValues.number(raw, "passPercent", PREFIX + "passPercent"),
                MapValues.bool(raw, "shuffleQuestions", PREFIX + "shuffleQuestions", defaults.shuffleQuestions()),
                MapValues.bool(raw, "shuffleAnswers", PREFIX + "shuffleAnswers", defaults.shuffleAnswers()),
                perPage == null ? defaults.questionsPerPage() : perPage,
                maxScore == null ? defaults.maxScore() : BigDecimal.valueOf(maxScore),
                review(MapValues.object(raw, "review", PREFIX + "review")),
                MapValues.uuid(raw, "gradeCategoryId", PREFIX + "gradeCategoryId"));
    }

    private static ReviewPolicy review(Map<String, Object> raw) {
        ReviewPolicy defaults = ReviewPolicy.DEFAULT;
        if (raw == null) {
            return defaults;
        }
        return new ReviewPolicy(
                timing(raw, "whenScore", defaults.whenScore()),
                timing(raw, "whenCorrectness", defaults.whenCorrectness()),
                timing(raw, "whenCorrectAnswers", defaults.whenCorrectAnswers()),
                timing(raw, "whenFeedback", defaults.whenFeedback()));
    }

    private static ReviewTiming timing(Map<String, Object> raw, String key, ReviewTiming fallback) {
        String field = PREFIX + "review." + key;
        String value = MapValues.string(raw, key, field);
        if (value == null) {
            return fallback;
        }
        return ReviewTiming.find(value).orElseThrow(() -> ValidationException.single(field, "invalid", "Unknown review timing"));
    }

    private static <E> E enumValue(Map<String, Object> raw, String key, Function<String, Optional<E>> finder, E fallback) {
        String value = MapValues.string(raw, key, PREFIX + key);
        if (value == null) {
            return fallback;
        }
        return finder.apply(value).orElseThrow(() -> ValidationException.single(PREFIX + key, "invalid", "Unknown value"));
    }
}
