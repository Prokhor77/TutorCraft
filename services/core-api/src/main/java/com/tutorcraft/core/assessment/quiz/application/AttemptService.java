package com.tutorcraft.core.assessment.quiz.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.assessment.quiz.application.AttemptRepository.AnswerSave;
import com.tutorcraft.core.assessment.quiz.application.QuizViews.AttemptResultView;
import com.tutorcraft.core.assessment.quiz.application.QuizViews.AttemptView;
import com.tutorcraft.core.assessment.quiz.application.QuizViews.SavedAnswerView;
import com.tutorcraft.core.assessment.quiz.domain.Attempt;
import com.tutorcraft.core.assessment.quiz.domain.QuestionResponse;
import com.tutorcraft.core.assessment.quiz.domain.QuizRules;
import com.tutorcraft.core.shared.config.AppProperties;
import com.tutorcraft.core.shared.domain.ConflictException;
import com.tutorcraft.core.shared.domain.DomainException;
import com.tutorcraft.core.shared.domain.NotFoundException;
import com.tutorcraft.core.shared.idempotency.IdempotencyService;
import com.tutorcraft.core.shared.idempotency.IdempotencyService.IdempotencyScope;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Прохождение попытки: просмотр, автосохранение ответа (UX-03, NFR-PERF-02), завершение (API-05). */
@Service
public class AttemptService {

    private static final String FINISH_OPERATION = "quiz.attempt.finish";

    private final AttemptRepository attempts;
    private final AttemptViewAssembler views;
    private final AttemptFinisher finisher;
    private final AttemptResultService results;
    private final EssayResponses essays;
    private final AccessService access;
    private final CurrentUserProvider currentUser;
    private final IdempotencyService idempotency;
    private final Clock clock;
    private final Duration grace;

    public AttemptService(AttemptRepository attempts, AttemptViewAssembler views, AttemptFinisher finisher,
                          AttemptResultService results, EssayResponses essays, AccessService access,
                          CurrentUserProvider currentUser, IdempotencyService idempotency, Clock clock, AppProperties properties) {
        this.attempts = attempts;
        this.views = views;
        this.finisher = finisher;
        this.results = results;
        this.essays = essays;
        this.access = access;
        this.currentUser = currentUser;
        this.idempotency = idempotency;
        this.clock = clock;
        this.grace = properties.quiz().timeGrace();
    }

    /** Владелец или преподаватель с quiz.viewReports. Ключей в ответе нет ни для кого (NFR-SEC-08). */
    public AttemptView get(UUID attemptId) {
        CurrentUser user = currentUser.require();
        Attempt attempt = attempts.find(user.tenantId(), attemptId).orElseThrow(AttemptService::notFound);
        if (!attempt.ownedBy(user.userId())) {
            access.require(Permission.QUIZ_VIEW_REPORTS, AccessContext.course(attempt.courseId()));
        }
        return views.assemble(attempt);
    }

    /**
     * Автосохранение: один UPDATE с условиями «владелец, попытка идёт, срок + допуск не истёк» (AC-4, AC-10).
     * Причина отказа выясняется только при неуспехе; истёкший срок сообщается раньше завершения попытки сервером.
     */
    @Transactional
    public SavedAnswerView saveAnswer(UUID attemptId, int slot, Map<String, Object> rawResponse, Boolean flagged) {
        CurrentUser user = currentUser.require();
        QuestionResponse response = rawResponse == null ? null : QuestionResponse.parse(rawResponse);
        Map<String, Object> stored = response == null ? null : essays.prepare(user, attemptId, response);
        Instant now = clock.instant();
        AnswerSave save = new AnswerSave(user.tenantId(), user.userId(), attemptId, slot, stored, flagged, now, now.minus(grace));
        if (attempts.saveAnswer(save)) {
            return new SavedAnswerView(now);
        }
        throw rejection(user, attemptId, slot, now);
    }

    @Transactional
    public AttemptResultView finish(UUID attemptId, String idempotencyKey) {
        CurrentUser user = currentUser.require();
        Attempt attempt = requireOwn(user, attemptId);
        access.require(Permission.QUIZ_ATTEMPT, AccessContext.course(attempt.courseId()));
        IdempotencyScope scope = new IdempotencyScope(user.tenantId(), user.userId(), FINISH_OPERATION);
        return idempotency.execute(scope, idempotencyKey, Map.of("attemptId", attemptId), AttemptResultView.class, () -> {
            Attempt finished = finisher.finish(user.tenantId(), attemptId, clock.instant());
            return results.resultOf(user, finished);
        });
    }

    private DomainException rejection(CurrentUser user, UUID attemptId, int slot, Instant now) {
        Attempt attempt = requireOwn(user, attemptId);
        if (!QuizRules.acceptsAnswers(attempt.timeDue(), now, grace)) {
            return new ConflictException(QuizErrors.TIME_EXPIRED, "Time is over", Map.of("timeDue", attempt.timeDue().toString()));
        }
        if (!attempt.inProgress()) {
            return new ConflictException(QuizErrors.ATTEMPT_FINISHED, "Attempt is already finished");
        }
        return new NotFoundException(QuizErrors.SLOT_NOT_FOUND, "Question slot not found", Map.of("slot", slot));
    }

    Attempt requireOwn(CurrentUser user, UUID attemptId) {
        return attempts.find(user.tenantId(), attemptId).filter(attempt -> attempt.ownedBy(user.userId()))
                .orElseThrow(AttemptService::notFound);
    }

    static NotFoundException notFound() {
        return new NotFoundException(QuizErrors.ATTEMPT_NOT_FOUND, "Attempt not found");
    }
}
