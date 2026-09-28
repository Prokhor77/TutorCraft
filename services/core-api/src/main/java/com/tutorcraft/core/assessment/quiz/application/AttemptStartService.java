package com.tutorcraft.core.assessment.quiz.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.assessment.quiz.application.QuizItems.QuizItem;
import com.tutorcraft.core.assessment.quiz.application.QuizViews.AttemptView;
import com.tutorcraft.core.assessment.quiz.domain.Attempt;
import com.tutorcraft.core.assessment.quiz.domain.AttemptSlot;
import com.tutorcraft.core.assessment.quiz.domain.AttemptState;
import com.tutorcraft.core.assessment.quiz.domain.QuizLayout;
import com.tutorcraft.core.assessment.quiz.domain.QuizRules;
import com.tutorcraft.core.shared.config.AppProperties;
import com.tutorcraft.core.shared.domain.BusinessRuleException;
import com.tutorcraft.core.shared.domain.ConflictException;
import com.tutorcraft.core.shared.domain.Ids;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Начать или продолжить попытку (FR-QUIZ-03): незавершённая попытка в пределах срока продолжается;
 * просроченная завершается сервером, после чего проверяются окно теста и лимит попыток (с исключениями, FR-QUIZ-06).
 */
@Service
public class AttemptStartService {

    private static final Logger log = LoggerFactory.getLogger(AttemptStartService.class);

    private final QuizItems quizzes;
    private final AttemptRepository attempts;
    private final QuizLayoutRepository layouts;
    private final QuizRulesResolver rulesResolver;
    private final QuestionDrawer drawer;
    private final AttemptFinisher finisher;
    private final AttemptViewAssembler views;
    private final AccessService access;
    private final CurrentUserProvider currentUser;
    private final Clock clock;
    private final Duration grace;

    public AttemptStartService(QuizItems quizzes, AttemptRepository attempts, QuizLayoutRepository layouts,
                               QuizRulesResolver rulesResolver, QuestionDrawer drawer, AttemptFinisher finisher,
                               AttemptViewAssembler views, AccessService access, CurrentUserProvider currentUser, Clock clock,
                               AppProperties properties) {
        this.quizzes = quizzes;
        this.attempts = attempts;
        this.layouts = layouts;
        this.rulesResolver = rulesResolver;
        this.drawer = drawer;
        this.finisher = finisher;
        this.views = views;
        this.access = access;
        this.currentUser = currentUser;
        this.clock = clock;
        this.grace = properties.quiz().timeGrace();
    }

    /** Отказ в новой попытке не откатывает завершение просроченной (оно уже произошло по правилам сервера). */
    @Transactional(noRollbackFor = BusinessRuleException.class)
    public AttemptView startOrContinue(UUID itemId) {
        CurrentUser user = currentUser.require();
        QuizItem quiz = quizzes.require(user.tenantId(), itemId);
        access.require(Permission.QUIZ_ATTEMPT, quiz.context());
        quizzes.requireLearnerAccess(user.tenantId(), user.userId(), quiz.item());
        Instant now = clock.instant();
        Optional<Attempt> open = attempts.findInProgress(user.tenantId(), itemId, user.userId());
        if (open.isPresent() && QuizRules.acceptsAnswers(open.get().timeDue(), now, grace)) {
            return views.assemble(open.get());
        }
        open.ifPresent(expired -> finisher.finish(user.tenantId(), expired.id(), now));
        return views.assemble(create(user, quiz, now));
    }

    private Attempt create(CurrentUser user, QuizItem quiz, Instant now) {
        QuizRules rules = rulesResolver.rulesFor(user.tenantId(), quiz, user.userId());
        int used = attempts.countAttempts(user.tenantId(), quiz.id(), user.userId());
        rules.startDenial(now, used).ifPresent(code -> {
            throw new BusinessRuleException(code, "Attempt cannot be started");
        });
        UUID attemptId = Ids.newId();
        QuizLayout layout = layouts.find(user.tenantId(), quiz.id()).orElse(QuizLayout.empty());
        List<AttemptSlot> slots = drawer.draw(user.tenantId(), quiz.courseId(), attemptId, layout, quiz.settings());
        if (slots.isEmpty()) {
            throw new BusinessRuleException(QuizErrors.NO_QUESTIONS, "Quiz has no questions");
        }
        Attempt attempt = new Attempt(attemptId, user.tenantId(), quiz.courseId(), quiz.id(), user.userId(), used + 1,
                AttemptState.IN_PROGRESS, now, rules.timeDue(now), null, null, quiz.settings().maxScore(), false, slots);
        if (!attempts.insert(attempt)) {
            return attempts.findInProgress(user.tenantId(), quiz.id(), user.userId())
                    .orElseThrow(() -> new ConflictException(QuizErrors.ATTEMPT_IN_PROGRESS, "Attempt is being started"));
        }
        log.info("Attempt {} started for item {} ({} slots)", attemptId, quiz.id(), slots.size());
        return attempt;
    }
}
