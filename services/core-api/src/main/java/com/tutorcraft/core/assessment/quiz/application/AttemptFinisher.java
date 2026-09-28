package com.tutorcraft.core.assessment.quiz.application;

import com.tutorcraft.core.assessment.AssessmentEvents.AttemptFinished;
import com.tutorcraft.core.assessment.quiz.application.AttemptRepository.AnswerGrade;
import com.tutorcraft.core.assessment.quiz.domain.AnswerRecord;
import com.tutorcraft.core.assessment.quiz.domain.Attempt;
import com.tutorcraft.core.assessment.quiz.domain.AttemptScoring;
import com.tutorcraft.core.assessment.quiz.domain.AttemptScoring.AttemptScore;
import com.tutorcraft.core.assessment.quiz.domain.AttemptScoring.SlotScore;
import com.tutorcraft.core.assessment.quiz.domain.AttemptSlot;
import com.tutorcraft.core.assessment.quiz.domain.QuestionVersion;
import com.tutorcraft.core.shared.domain.NotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Завершение попытки (FR-QUIZ-04): автопроверка сохранённых ответов, эссе — в ручную проверку, балл попытки,
 * итоговая оценка в журнал, событие {@link AttemptFinished}. Используется студентом, фоновой задачей (AC-4) и при старте.
 */
@Component
public class AttemptFinisher {

    private static final Logger log = LoggerFactory.getLogger(AttemptFinisher.class);

    private final AttemptRepository attempts;
    private final QuestionRepository questions;
    private final QuizGradeSync gradeSync;
    private final ApplicationEventPublisher events;

    public AttemptFinisher(AttemptRepository attempts, QuestionRepository questions, QuizGradeSync gradeSync,
                           ApplicationEventPublisher events) {
        this.attempts = attempts;
        this.questions = questions;
        this.gradeSync = gradeSync;
        this.events = events;
    }

    /** Идемпотентно: уже завершённая попытка возвращается как есть. */
    @Transactional
    public Attempt finish(UUID tenantId, UUID attemptId, Instant now) {
        Attempt attempt = attempts.lock(tenantId, attemptId)
                .orElseThrow(() -> new NotFoundException(QuizErrors.ATTEMPT_NOT_FOUND, "Attempt not found"));
        if (!attempt.inProgress()) {
            return attempt;
        }
        AttemptScore total = gradeAnswers(attempt, now);
        attempts.markFinished(tenantId, attemptId, now, total.score(), total.needsManualGrading());
        gradeSync.sync(tenantId, attempt.itemId(), attempt.userId(), null);
        events.publishEvent(new AttemptFinished(tenantId, attempt.courseId(), attempt.itemId(), attempt.userId(), attemptId));
        log.info("Attempt {} finished, manual grading pending: {}", attemptId, total.needsManualGrading());
        return attempts.find(tenantId, attemptId).orElseThrow();
    }

    /**
     * Проверяет все слоты по версиям, записанным в ответах, и сохраняет результаты.
     * @param keepManual сохранить ручные оценки эссе (переоценка)
     */
    public AttemptScore regradeAnswers(Attempt attempt, Map<Integer, UUID> versionOverrides, boolean keepManual, Instant now) {
        List<AnswerRecord> answers = attempts.answers(attempt.tenantId(), attempt.id());
        Map<Integer, AnswerRecord> bySlot = answers.stream().collect(Collectors.toMap(AnswerRecord::slot, Function.identity()));
        Map<UUID, QuestionVersion> versions = questions.findVersions(attempt.tenantId(), answers.stream()
                .map(answer -> versionOverrides.getOrDefault(answer.slot(), answer.questionVersionId())).toList());
        List<SlotScore> scores = new ArrayList<>();
        for (AttemptSlot slot : attempt.layout()) {
            AnswerRecord answer = bySlot.get(slot.slot());
            QuestionVersion version = answer == null ? null
                    : versions.get(versionOverrides.getOrDefault(slot.slot(), answer.questionVersionId()));
            if (version == null) {
                continue;
            }
            AnswerGrade grade = SlotGrading.grade(slot, answer, version, keepManual, now);
            attempts.updateAnswerGrade(attempt.tenantId(), grade);
            scores.add(SlotGrading.score(slot, grade));
        }
        return AttemptScoring.total(scores, attempt.maxScore());
    }

    /** Балл попытки по уже сохранённым оценкам слотов (после ручной проверки эссе). */
    public AttemptScore storedTotal(Attempt attempt) {
        Map<Integer, AnswerRecord> bySlot = attempts.answers(attempt.tenantId(), attempt.id()).stream()
                .collect(Collectors.toMap(AnswerRecord::slot, Function.identity()));
        List<SlotScore> scores = attempt.layout().stream().filter(slot -> bySlot.containsKey(slot.slot()))
                .map(slot -> new SlotScore(slot.points(), bySlot.get(slot.slot()).score())).toList();
        return AttemptScoring.total(scores, attempt.maxScore());
    }

    private AttemptScore gradeAnswers(Attempt attempt, Instant now) {
        return regradeAnswers(attempt, Map.of(), false, now);
    }
}
