package com.tutorcraft.core.assessment.quiz.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Правила показа результатов (FR-QUIZ-05). */
public record ReviewPolicy(ReviewTiming whenScore, ReviewTiming whenCorrectness, ReviewTiming whenCorrectAnswers,
                           ReviewTiming whenFeedback) {

    public static final ReviewPolicy DEFAULT = new ReviewPolicy(ReviewTiming.IMMEDIATELY, ReviewTiming.IMMEDIATELY,
            ReviewTiming.AFTER_CLOSE, ReviewTiming.IMMEDIATELY);

    /** Что можно показать студенту для завершённой попытки в момент {@code now}. */
    public Shown shown(Instant closeAt, Instant now) {
        return new Shown(whenScore.allows(closeAt, now), whenCorrectness.allows(closeAt, now),
                whenCorrectAnswers.allows(closeAt, now), whenFeedback.allows(closeAt, now));
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("whenScore", whenScore.key());
        map.put("whenCorrectness", whenCorrectness.key());
        map.put("whenCorrectAnswers", whenCorrectAnswers.key());
        map.put("whenFeedback", whenFeedback.key());
        return map;
    }

    public record Shown(boolean score, boolean correctness, boolean correctAnswers, boolean feedback) {

        public static final Shown ALL = new Shown(true, true, true, true);
    }
}
