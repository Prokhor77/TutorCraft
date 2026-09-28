package com.tutorcraft.core.assessment.quiz.domain;

import java.math.BigDecimal;
import java.util.UUID;

/** Слот состава теста (FR-QUIZ-02): конкретный вопрос банка или N случайных из папки/по тегу. */
public sealed interface LayoutSlot {

    /** Баллы за вопрос; null — «по умолчанию для вопроса». */
    BigDecimal points();

    /** Номер страницы; null — по настройке «вопросов на странице». */
    Integer page();

    record Fixed(UUID questionId, BigDecimal points, Integer page) implements LayoutSlot {
    }

    record Random(UUID categoryId, String tag, int count, BigDecimal points, Integer page) implements LayoutSlot {
    }
}
