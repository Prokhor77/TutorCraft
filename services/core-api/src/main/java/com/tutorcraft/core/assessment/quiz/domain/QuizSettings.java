package com.tutorcraft.core.assessment.quiz.domain;

import com.tutorcraft.core.shared.domain.Validator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Настройки теста (контракт §10, QuizSettings). Умолчания (UX-02): без окна и лимита времени, попытки не ограничены,
 * лучшая попытка, 5 вопросов на странице, 10 баллов, балл — сразу, правильные ответы — после закрытия.
 */
public record QuizSettings(Instant openAt, Instant closeAt, Integer timeLimitSec, Integer maxAttempts,
                           GradingMethod gradingMethod, Double passPercent, boolean shuffleQuestions, boolean shuffleAnswers,
                           int questionsPerPage, BigDecimal maxScore, ReviewPolicy review, UUID gradeCategoryId) {

    public static final String KIND = "quiz";
    public static final int DEFAULT_QUESTIONS_PER_PAGE = 5;
    public static final BigDecimal DEFAULT_MAX_SCORE = BigDecimal.valueOf(10.0);
    public static final int MIN_TIME_LIMIT_SEC = 60;
    public static final int MAX_TIME_LIMIT_SEC = 7 * 24 * 60 * 60;
    public static final int MAX_ATTEMPTS_LIMIT = 100;
    static final int MAX_QUESTIONS_PER_PAGE = 100;
    static final double MAX_SCORE_LIMIT = 10_000;
    static final double MAX_PERCENT = 100;

    public static QuizSettings defaults() {
        return new QuizSettings(null, null, null, null, GradingMethod.HIGHEST, null, false, true,
                DEFAULT_QUESTIONS_PER_PAGE, DEFAULT_MAX_SCORE, ReviewPolicy.DEFAULT, null);
    }

    /** Разбор с умолчаниями и валидацией (DATA-04). Поля ошибок — {@code settings.<name>}. */
    public static QuizSettings parse(Map<String, Object> raw) {
        QuizSettings parsed = QuizSettingsParser.parse(raw == null ? Map.of() : raw);
        parsed.validate();
        return parsed;
    }

    private void validate() {
        new Validator()
            .check(timeLimitSec == null || (timeLimitSec >= MIN_TIME_LIMIT_SEC && timeLimitSec <= MAX_TIME_LIMIT_SEC),
                    "settings.timeLimitSec", "out_of_range", "Time limit must be between 1 minute and 7 days")
            .check(maxAttempts == null || (maxAttempts >= 1 && maxAttempts <= MAX_ATTEMPTS_LIMIT),
                    "settings.maxAttempts", "out_of_range", "Attempts must be between 1 and " + MAX_ATTEMPTS_LIMIT)
            .check(passPercent == null || (passPercent >= 0 && passPercent <= MAX_PERCENT),
                    "settings.passPercent", "out_of_range", "Pass percent must be between 0 and 100")
            .check(questionsPerPage >= 1 && questionsPerPage <= MAX_QUESTIONS_PER_PAGE,
                    "settings.questionsPerPage", "out_of_range", "Questions per page must be between 1 and " + MAX_QUESTIONS_PER_PAGE)
            .check(maxScore.signum() > 0 && maxScore.doubleValue() <= MAX_SCORE_LIMIT,
                    "settings.maxScore", "out_of_range", "Max score must be positive and at most " + (int) MAX_SCORE_LIMIT)
            .check(openAt == null || closeAt == null || openAt.isBefore(closeAt),
                    "settings.closeAt", "before_open", "Close date must be after open date")
            .throwIfInvalid();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("kind", KIND);
        map.put("openAt", openAt == null ? null : openAt.toString());
        map.put("closeAt", closeAt == null ? null : closeAt.toString());
        map.put("timeLimitSec", timeLimitSec);
        map.put("maxAttempts", maxAttempts);
        map.put("gradingMethod", gradingMethod.key());
        map.put("passPercent", passPercent);
        map.put("shuffleQuestions", shuffleQuestions);
        map.put("shuffleAnswers", shuffleAnswers);
        map.put("questionsPerPage", questionsPerPage);
        map.put("maxScore", maxScore.doubleValue());
        map.put("review", review.toMap());
        map.put("gradeCategoryId", gradeCategoryId == null ? null : gradeCategoryId.toString());
        return map;
    }
}
