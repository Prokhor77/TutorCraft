package com.tutorcraft.core.assessment.quiz.application;

import com.tutorcraft.core.assessment.quiz.application.AttemptRepository.AnswerGrade;
import com.tutorcraft.core.assessment.quiz.domain.AnswerRecord;
import com.tutorcraft.core.assessment.quiz.domain.AttemptScoring;
import com.tutorcraft.core.assessment.quiz.domain.AttemptScoring.SlotScore;
import com.tutorcraft.core.assessment.quiz.domain.AttemptSlot;
import com.tutorcraft.core.assessment.quiz.domain.GradeOutcome;
import com.tutorcraft.core.assessment.quiz.domain.QuestionGrader;
import com.tutorcraft.core.assessment.quiz.domain.QuestionResponse;
import com.tutorcraft.core.assessment.quiz.domain.QuestionType;
import com.tutorcraft.core.assessment.quiz.domain.QuestionVersion;
import java.time.Instant;

/** Автопроверка одного слота попытки по заданной версии вопроса. */
final class SlotGrading {

    private SlotGrading() {
    }

    /**
     * @param keepManual сохранить уже выставленную вручную оценку эссе (переоценка после исправления ключа)
     */
    static AnswerGrade grade(AttemptSlot slot, AnswerRecord answer, QuestionVersion version, boolean keepManual, Instant now) {
        if (keepManual && version.type() == QuestionType.ESSAY && answer.gradedBy() != null) {
            return new AnswerGrade(answer.attemptId(), answer.slot(), version.id(), answer.fraction(), answer.score(), true,
                    answer.gradedBy(), answer.comment(), now);
        }
        QuestionResponse response = answer.response() == null ? null : QuestionResponse.parse(answer.response());
        GradeOutcome outcome = QuestionGrader.grade(version.data(), response);
        if (outcome.manual()) {
            return new AnswerGrade(answer.attemptId(), answer.slot(), version.id(), null, null, true, null, null, now);
        }
        return new AnswerGrade(answer.attemptId(), answer.slot(), version.id(), outcome.fraction(),
                AttemptScoring.slotScore(slot.points(), outcome.fraction()), false, null, null, now);
    }

    static SlotScore score(AttemptSlot slot, AnswerGrade grade) {
        return new SlotScore(slot.points(), grade.score());
    }
}
