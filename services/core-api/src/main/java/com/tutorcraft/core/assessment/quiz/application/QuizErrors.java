package com.tutorcraft.core.assessment.quiz.application;

/** Коды ошибок тестов и банка вопросов (тексты — i18n/assessment*.properties). */
public final class QuizErrors {

    public static final String NOT_FOUND = "quiz.not_found";
    public static final String UNAVAILABLE = "quiz.unavailable";
    public static final String QUESTION_NOT_FOUND = "quiz.question_not_found";
    public static final String CATEGORY_NOT_FOUND = "quiz.category_not_found";
    public static final String ATTEMPT_NOT_FOUND = "quiz.attempt_not_found";
    public static final String SLOT_NOT_FOUND = "quiz.slot_not_found";
    public static final String OVERRIDE_NOT_FOUND = "quiz.override_not_found";
    public static final String TIME_EXPIRED = "quiz.time_expired";
    public static final String ATTEMPT_FINISHED = "quiz.attempt_finished";
    public static final String ATTEMPT_IN_PROGRESS = "quiz.attempt_in_progress";
    public static final String NO_QUESTIONS = "quiz.no_questions";
    public static final String NOT_MANUAL = "quiz.not_manual";
    public static final String ITEM_NOT_FOUND = "item.not_found";

    private QuizErrors() {
    }
}
