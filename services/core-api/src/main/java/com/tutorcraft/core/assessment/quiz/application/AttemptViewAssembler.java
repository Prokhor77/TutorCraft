package com.tutorcraft.core.assessment.quiz.application;

import com.tutorcraft.core.assessment.quiz.application.QuizViews.AttemptView;
import com.tutorcraft.core.assessment.quiz.application.QuizViews.StudentQuestionView;
import com.tutorcraft.core.assessment.quiz.domain.AnswerRecord;
import com.tutorcraft.core.assessment.quiz.domain.Attempt;
import com.tutorcraft.core.assessment.quiz.domain.AttemptSlot;
import com.tutorcraft.core.assessment.quiz.domain.PublicQuestionParts;
import com.tutorcraft.core.assessment.quiz.domain.QuestionVersion;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Представление попытки для студента (контракт §10, Attempt). Ключи ответов не попадают в ответ ни при каком
 * состоянии попытки (NFR-SEC-08): используются только {@link PublicQuestionParts}.
 */
@Component
public class AttemptViewAssembler {

    private final AttemptRepository attempts;
    private final QuestionRepository questions;
    private final Clock clock;

    public AttemptViewAssembler(AttemptRepository attempts, QuestionRepository questions, Clock clock) {
        this.attempts = attempts;
        this.questions = questions;
        this.clock = clock;
    }

    public AttemptView assemble(Attempt attempt) {
        Map<Integer, AnswerRecord> answers = attempts.answers(attempt.tenantId(), attempt.id()).stream()
                .collect(Collectors.toMap(AnswerRecord::slot, Function.identity()));
        Map<UUID, QuestionVersion> versions = questions.findVersions(attempt.tenantId(),
                answers.values().stream().map(AnswerRecord::questionVersionId).collect(Collectors.toSet()));
        List<StudentQuestionView> views = attempt.layout().stream()
                .filter(slot -> answers.containsKey(slot.slot()))
                .map(slot -> question(slot, answers.get(slot.slot()), versions.get(answers.get(slot.slot()).questionVersionId())))
                .filter(Objects::nonNull)
                .toList();
        return new AttemptView(attempt.id(), attempt.itemId(), attempt.number(), attempt.state().key(), attempt.startedAt(),
                attempt.timeDue(), clock.instant(), views, attempt.totalPages());
    }

    static StudentQuestionView question(AttemptSlot slot, AnswerRecord answer, QuestionVersion version) {
        if (version == null) {
            return null;
        }
        PublicQuestionParts parts = PublicQuestionParts.of(version.data(), slot.optionOrder());
        return new StudentQuestionView(slot.slot(), slot.page(), slot.points(), version.type().key(), version.title(),
                version.body(), parts.options(), parts.prompts(), parts.answerChoices(), parts.items(), parts.responseFormat(),
                answer.response(), answer.flagged());
    }
}
