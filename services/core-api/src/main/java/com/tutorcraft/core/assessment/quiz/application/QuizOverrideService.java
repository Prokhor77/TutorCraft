package com.tutorcraft.core.assessment.quiz.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.assessment.quiz.application.QuizItems.QuizItem;
import com.tutorcraft.core.assessment.quiz.application.QuizViews.OverrideView;
import com.tutorcraft.core.assessment.quiz.domain.QuizOverride;
import com.tutorcraft.core.assessment.quiz.domain.QuizSettings;
import com.tutorcraft.core.audit.AuditLog;
import com.tutorcraft.core.audit.AuditRecord;
import com.tutorcraft.core.enrollment.EnrollmentApi;
import com.tutorcraft.core.shared.domain.Ids;
import com.tutorcraft.core.shared.domain.NotFoundException;
import com.tutorcraft.core.shared.domain.Validator;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Индивидуальные исключения теста для студента/группы (FR-QUIZ-06). Требует {@code quiz.manage}. */
@Service
public class QuizOverrideService {

    private final QuizItems quizzes;
    private final QuizOverrideRepository overrides;
    private final EnrollmentApi enrollment;
    private final AccessService access;
    private final CurrentUserProvider currentUser;
    private final AuditLog audit;

    public QuizOverrideService(QuizItems quizzes, QuizOverrideRepository overrides, EnrollmentApi enrollment,
                               AccessService access, CurrentUserProvider currentUser, AuditLog audit) {
        this.quizzes = quizzes;
        this.overrides = overrides;
        this.enrollment = enrollment;
        this.access = access;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    /** Создаёт или заменяет исключение пользователя/группы. */
    @Transactional
    public OverrideView save(UUID itemId, OverrideRequest request) {
        CurrentUser user = currentUser.require();
        QuizItem quiz = requireManage(user, itemId);
        validate(request);
        if (request.userId() != null && enrollment.membership(user.tenantId(), quiz.courseId(), request.userId()).isEmpty()) {
            throw new NotFoundException("user.not_found", "User is not a member of the course");
        }
        QuizOverride override = new QuizOverride(Ids.newId(), request.userId(), request.groupId(), request.openAt(),
                request.closeAt(), request.timeLimitSec(), request.maxAttempts());
        QuizOverride saved = overrides.upsert(user.tenantId(), quiz.courseId(), itemId, override, user.userId());
        audit.record(AuditRecord.of(user.tenantId(), user.userId(), "quiz.override_saved", "item", itemId.toString()));
        return view(saved);
    }

    public List<OverrideView> list(UUID itemId) {
        CurrentUser user = currentUser.require();
        requireManage(user, itemId);
        return overrides.listOfItem(user.tenantId(), itemId).stream().map(QuizOverrideService::view).toList();
    }

    @Transactional
    public void delete(UUID itemId, UUID overrideId) {
        CurrentUser user = currentUser.require();
        requireManage(user, itemId);
        if (!overrides.delete(user.tenantId(), itemId, overrideId)) {
            throw new NotFoundException(QuizErrors.OVERRIDE_NOT_FOUND, "Override not found");
        }
        audit.record(AuditRecord.of(user.tenantId(), user.userId(), "quiz.override_deleted", "item", itemId.toString()));
    }

    private QuizItem requireManage(CurrentUser user, UUID itemId) {
        QuizItem quiz = quizzes.require(user.tenantId(), itemId);
        access.require(Permission.QUIZ_MANAGE, quiz.context());
        return quiz;
    }

    private static void validate(OverrideRequest request) {
        new Validator()
            .check((request.userId() == null) != (request.groupId() == null), "userId", "exactly_one",
                    "Specify either userId or groupId")
            .check(request.timeLimitSec() == null
                    || (request.timeLimitSec() >= QuizSettings.MIN_TIME_LIMIT_SEC && request.timeLimitSec() <= QuizSettings.MAX_TIME_LIMIT_SEC),
                    "timeLimitSec", "out_of_range", "Time limit must be between 1 minute and 7 days")
            .check(request.maxAttempts() == null || (request.maxAttempts() >= 1 && request.maxAttempts() <= QuizSettings.MAX_ATTEMPTS_LIMIT),
                    "maxAttempts", "out_of_range", "Attempts must be between 1 and " + QuizSettings.MAX_ATTEMPTS_LIMIT)
            .check(request.openAt() == null || request.closeAt() == null || request.openAt().isBefore(request.closeAt()),
                    "closeAt", "before_open", "Close date must be after open date")
            .check(request.hasAnyChange(), "openAt", "empty_override", "Override must change at least one setting")
            .throwIfInvalid();
    }

    private static OverrideView view(QuizOverride override) {
        return new OverrideView(override.id(), override.userId(), override.groupId(), override.openAt(), override.closeAt(),
                override.timeLimitSec(), override.maxAttempts());
    }

    public record OverrideRequest(UUID userId, UUID groupId, Instant openAt, Instant closeAt, Integer timeLimitSec,
                                  Integer maxAttempts) {

        boolean hasAnyChange() {
            return openAt != null || closeAt != null || timeLimitSec != null || maxAttempts != null;
        }
    }
}
