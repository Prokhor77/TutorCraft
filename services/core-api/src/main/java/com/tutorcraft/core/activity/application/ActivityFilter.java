package com.tutorcraft.core.activity.application;

import com.tutorcraft.core.activity.domain.ActivityKind;
import com.tutorcraft.core.activity.domain.ActivityOutcome;
import java.time.Instant;
import java.util.UUID;

/**
 * Фильтр поиска по журналу.
 *
 * @param actor      подстрока e-mail / имени пользователя или точный IP
 * @param route      подстрока шаблона маршрута или страницы
 * @param allTenants записи всех школ, а не только выбранной (только главный администратор платформы)
 */
public record ActivityFilter(UUID userId, String actor, ActivityKind kind, ActivityOutcome outcome, Integer status,
                             String route, String requestId, String sessionId, Instant from, Instant to,
                             boolean includeAnonymous, boolean allTenants) {

    public ActivityOutcome outcomeOrAll() {
        return outcome == null ? ActivityOutcome.ALL : outcome;
    }
}
