package com.tutorcraft.core.assessment.quiz.application;

import com.tutorcraft.core.assessment.quiz.application.QuizViews.QuestionView;
import com.tutorcraft.core.assessment.quiz.domain.QuestionVersion;
import com.tutorcraft.core.assessment.quiz.domain.StoredQuestion;

/** Маппинг вопроса банка в представление преподавателя (с ключами). */
final class QuestionMapper {

    private QuestionMapper() {
    }

    static QuestionView view(StoredQuestion question, QuestionVersion version) {
        return new QuestionView(question.id(), version.version(), version.id(), version.type().key(), version.title(),
                version.body(), version.defaultScore(), question.categoryId(), question.tags(), version.data().toMap(),
                version.generalFeedback());
    }
}
