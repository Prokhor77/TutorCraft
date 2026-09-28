package com.tutorcraft.core.assessment.quiz.application;

import com.tutorcraft.core.assessment.quiz.application.AttemptRepository.ItemAttemptState;
import com.tutorcraft.core.courses.ItemRef;
import com.tutorcraft.core.courses.ItemType;
import com.tutorcraft.core.courses.spi.ItemStatusProvider;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Статус теста для студента: {@code not_started | in_progress | submitted} (завершён, ждёт проверки эссе) {@code | graded}.
 * Зависит только от репозитория попыток (правило против циклов).
 */
@Component
class QuizItemStatusProvider implements ItemStatusProvider {

    static final String NOT_STARTED = "not_started";
    static final String IN_PROGRESS = "in_progress";
    static final String SUBMITTED = "submitted";
    static final String GRADED = "graded";

    private final AttemptRepository attempts;

    QuizItemStatusProvider(AttemptRepository attempts) {
        this.attempts = attempts;
    }

    @Override
    public Set<ItemType> supportedTypes() {
        return Set.of(ItemType.QUIZ);
    }

    @Override
    public Map<UUID, String> statuses(UUID tenantId, UUID userId, List<ItemRef> items) {
        List<UUID> quizIds = items.stream().filter(item -> item.type() == ItemType.QUIZ).map(ItemRef::id).toList();
        Map<UUID, ItemAttemptState> states = quizIds.isEmpty() ? Map.of() : attempts.itemStates(tenantId, userId, quizIds);
        Map<UUID, String> result = new HashMap<>();
        quizIds.forEach(id -> result.put(id, status(states.get(id))));
        return result;
    }

    static String status(ItemAttemptState state) {
        if (state == null) {
            return NOT_STARTED;
        }
        if (state.inProgress()) {
            return IN_PROGRESS;
        }
        if (state.finished() == 0) {
            return NOT_STARTED;
        }
        return state.pendingManual() ? SUBMITTED : GRADED;
    }
}
