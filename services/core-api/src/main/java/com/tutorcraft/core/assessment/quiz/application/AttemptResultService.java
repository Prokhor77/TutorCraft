package com.tutorcraft.core.assessment.quiz.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.assessment.quiz.application.QuizItems.QuizItem;
import com.tutorcraft.core.assessment.quiz.application.QuizViews.AttemptResultView;
import com.tutorcraft.core.assessment.quiz.application.QuizViews.ResultQuestion;
import com.tutorcraft.core.assessment.quiz.domain.AnswerRecord;
import com.tutorcraft.core.assessment.quiz.domain.Attempt;
import com.tutorcraft.core.assessment.quiz.domain.AttemptScoring;
import com.tutorcraft.core.assessment.quiz.domain.AttemptSlot;
import com.tutorcraft.core.assessment.quiz.domain.CorrectResponses;
import com.tutorcraft.core.assessment.quiz.domain.QuestionResponse;
import com.tutorcraft.core.assessment.quiz.domain.QuestionVersion;
import com.tutorcraft.core.assessment.quiz.domain.QuizRules;
import com.tutorcraft.core.assessment.quiz.domain.GradeOutcome;
import com.tutorcraft.core.assessment.quiz.domain.ReviewPolicy.Shown;
import com.tutorcraft.core.shared.domain.ConflictException;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Результат попытки по правилам показа (FR-QUIZ-05). Для незавершённой попытки результата нет — ключи не покидают
 * сервер до завершения (NFR-SEC-08). Преподаватель с quiz.viewReports видит всё.
 */
@Service
public class AttemptResultService {

    private final AttemptRepository attempts;
    private final QuestionRepository questions;
    private final QuizItems quizzes;
    private final QuizRulesResolver rulesResolver;
    private final AccessService access;
    private final CurrentUserProvider currentUser;
    private final Clock clock;

    public AttemptResultService(AttemptRepository attempts, QuestionRepository questions, QuizItems quizzes,
                                QuizRulesResolver rulesResolver, AccessService access, CurrentUserProvider currentUser,
                                Clock clock) {
        this.attempts = attempts;
        this.questions = questions;
        this.quizzes = quizzes;
        this.rulesResolver = rulesResolver;
        this.access = access;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    public AttemptResultView result(UUID attemptId) {
        CurrentUser user = currentUser.require();
        Attempt attempt = attempts.find(user.tenantId(), attemptId).orElseThrow(AttemptService::notFound);
        boolean staff = attempt.ownedBy(user.userId())
                ? access.can(Permission.QUIZ_VIEW_REPORTS, AccessContext.course(attempt.courseId()))
                : requireReports(attempt);
        return staff ? fullResult(attempt) : resultOf(user, attempt);
    }

    /** Полный результат для проверяющего; права проверены вызывающим кодом. */
    public AttemptResultView fullResult(Attempt attempt) {
        return build(attempt, quizzes.require(attempt.tenantId(), attempt.itemId()), Shown.ALL);
    }

    /** Результат для владельца попытки по правилам показа с учётом его исключений (дата закрытия). */
    public AttemptResultView resultOf(CurrentUser user, Attempt attempt) {
        QuizItem quiz = quizzes.require(user.tenantId(), attempt.itemId());
        QuizRules rules = rulesResolver.rulesFor(user.tenantId(), quiz, attempt.userId());
        return build(attempt, quiz, quiz.settings().review().shown(rules.closeAt(), clock.instant()));
    }

    private boolean requireReports(Attempt attempt) {
        access.require(Permission.QUIZ_VIEW_REPORTS, AccessContext.course(attempt.courseId()));
        return true;
    }

    private AttemptResultView build(Attempt attempt, QuizItem quiz, Shown shown) {
        if (attempt.inProgress()) {
            throw new ConflictException(QuizErrors.ATTEMPT_IN_PROGRESS, "Attempt is not finished yet");
        }
        Map<Integer, AnswerRecord> answers = attempts.answers(attempt.tenantId(), attempt.id()).stream()
                .collect(Collectors.toMap(AnswerRecord::slot, Function.identity()));
        Map<UUID, QuestionVersion> versions = questions.findVersions(attempt.tenantId(),
                answers.values().stream().map(AnswerRecord::questionVersionId).collect(Collectors.toSet()));
        List<ResultQuestion> items = attempt.layout().stream()
                .map(slot -> question(slot, answers.get(slot.slot()), versions, shown))
                .filter(Objects::nonNull).toList();
        Double percent = shown.score() ? AttemptScoring.percent(attempt.score(), attempt.maxScore()) : null;
        Double passPercent = quiz.settings().passPercent();
        Boolean passed = percent == null || passPercent == null || attempt.needsManualGrading() ? null : percent >= passPercent;
        return new AttemptResultView(attempt.id(), attempt.state().key(), shown.score() ? attempt.score() : null,
                attempt.maxScore(), percent, passed, attempt.needsManualGrading(), items);
    }

    private static ResultQuestion question(AttemptSlot slot, AnswerRecord answer, Map<UUID, QuestionVersion> versions,
                                           Shown shown) {
        QuestionVersion version = answer == null ? null : versions.get(answer.questionVersionId());
        if (version == null) {
            return null;
        }
        Boolean correct = !shown.correctness() || answer.needsManual() || answer.fraction() == null
                ? null : answer.fraction() >= GradeOutcome.FULL;
        Map<String, Object> correctResponse = shown.correctAnswers()
                ? CorrectResponses.of(version.data()).map(QuestionResponse::toMap).orElse(null) : null;
        String feedback = shown.feedback() ? ResultFeedback.of(version, answer.response()) : null;
        return new ResultQuestion(slot.slot(), version.title(), shown.score() ? answer.score() : null, slot.points(), correct,
                answer.response(), correctResponse, feedback, shown.feedback() ? answer.comment() : null);
    }
}
