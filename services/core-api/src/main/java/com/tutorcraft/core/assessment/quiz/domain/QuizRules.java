package com.tutorcraft.core.assessment.quiz.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Действующие для студента окно, лимит времени и число попыток: настройки теста + исключения (FR-QUIZ-06).
 * Приоритет: исключение пользователя → самое мягкое из исключений его групп → настройки теста.
 */
public record QuizRules(Instant openAt, Instant closeAt, Integer timeLimitSec, Integer maxAttempts) {

    public static final String NOT_OPEN = "quiz.not_open";
    public static final String CLOSED = "quiz.closed";
    public static final String NO_ATTEMPTS_LEFT = "quiz.no_attempts_left";

    public static QuizRules effective(QuizSettings settings, List<QuizOverride> overrides) {
        Optional<QuizOverride> user = overrides.stream().filter(QuizOverride::forUser).findFirst();
        List<QuizOverride> groups = overrides.stream().filter(o -> !o.forUser()).toList();
        return new QuizRules(
                pick(user, groups, QuizOverride::openAt, Comparator.<Instant>reverseOrder(), settings.openAt()),
                pick(user, groups, QuizOverride::closeAt, Comparator.<Instant>naturalOrder(), settings.closeAt()),
                pick(user, groups, QuizOverride::timeLimitSec, Comparator.<Integer>naturalOrder(), settings.timeLimitSec()),
                pick(user, groups, QuizOverride::maxAttempts, Comparator.<Integer>naturalOrder(), settings.maxAttempts()));
    }

    /** Причина, по которой нельзя начать новую попытку (код ошибки), или пусто. */
    public Optional<String> startDenial(Instant now, int attemptsUsed) {
        if (openAt != null && now.isBefore(openAt)) {
            return Optional.of(NOT_OPEN);
        }
        if (closeAt != null && !now.isBefore(closeAt)) {
            return Optional.of(CLOSED);
        }
        if (maxAttempts != null && attemptsUsed >= maxAttempts) {
            return Optional.of(NO_ATTEMPTS_LEFT);
        }
        return Optional.empty();
    }

    /** Срок попытки: min(начало + лимит, закрытие теста); null — без срока. */
    public Instant timeDue(Instant startedAt) {
        Instant byLimit = timeLimitSec == null ? null : startedAt.plusSeconds(timeLimitSec);
        if (byLimit == null) {
            return closeAt;
        }
        return closeAt == null || byLimit.isBefore(closeAt) ? byLimit : closeAt;
    }

    /** Сервер принимает ответы до срока плюс допуск на сетевую задержку (AC-4). */
    public static boolean acceptsAnswers(Instant timeDue, Instant now, Duration grace) {
        return timeDue == null || !now.isAfter(timeDue.plus(grace));
    }

    /**
     * Значение поля: у исключения пользователя, иначе максимум по «мягкости» среди групп, иначе из настроек.
     * {@code leniency} упорядочивает значения так, что самое мягкое — наибольшее.
     */
    private static <T> T pick(Optional<QuizOverride> user, List<QuizOverride> groups, Function<QuizOverride, T> field,
                              Comparator<T> leniency, T fallback) {
        Optional<T> own = user.map(field);
        if (own.isPresent()) {
            return own.get();
        }
        return groups.stream().map(field).filter(Objects::nonNull).max(leniency).orElse(fallback);
    }
}
