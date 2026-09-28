package com.tutorcraft.core.assessment.quiz.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.assessment.quiz.application.QuizItems.QuizItem;
import com.tutorcraft.core.assessment.quiz.application.QuizViews.AttemptSummaryView;
import com.tutorcraft.core.assessment.quiz.domain.Attempt;
import com.tutorcraft.core.identity.UsersApi;
import com.tutorcraft.core.identity.UsersApi.UserRef;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.api.PageResponse;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Отчёт преподавателя: таблица попыток теста (FR-QUIZ-07). Ответы по вопросам — GET /attempts/{id}/result. */
@Service
public class QuizReportService {

    private final QuizItems quizzes;
    private final AttemptRepository attempts;
    private final UsersApi users;
    private final AccessService access;
    private final CurrentUserProvider currentUser;

    public QuizReportService(QuizItems quizzes, AttemptRepository attempts, UsersApi users, AccessService access,
                             CurrentUserProvider currentUser) {
        this.quizzes = quizzes;
        this.attempts = attempts;
        this.users = users;
        this.access = access;
        this.currentUser = currentUser;
    }

    public PageResponse<AttemptSummaryView> attempts(UUID itemId, PageQuery page) {
        CurrentUser user = currentUser.require();
        QuizItem quiz = quizzes.require(user.tenantId(), itemId);
        access.require(Permission.QUIZ_VIEW_REPORTS, quiz.context());
        PageResponse<Attempt> found = attempts.pageOfItem(user.tenantId(), itemId, page);
        Map<UUID, UserRef> names = users.findAll(user.tenantId(),
                found.items().stream().map(Attempt::userId).collect(Collectors.toSet()));
        return found.map(attempt -> new AttemptSummaryView(attempt.id(), attempt.userId(), displayName(names.get(attempt.userId())),
                attempt.number(), attempt.state().key(), attempt.startedAt(), attempt.finishedAt(), attempt.score(),
                attempt.maxScore()));
    }

    static String displayName(UserRef user) {
        return user == null ? "" : user.displayName();
    }
}
