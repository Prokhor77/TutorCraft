package com.tutorcraft.core.progress.application;

import com.tutorcraft.core.courses.ItemRef;
import com.tutorcraft.core.progress.ProgressEvents.ItemCompletionChanged;
import com.tutorcraft.core.progress.domain.CompletionRule;
import com.tutorcraft.core.progress.domain.ItemCompletion;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Применение событий к выполнению элемента (FR-PROG-01): пересчёт по правилу элемента, сохранение,
 * событие {@link ItemCompletionChanged} и проверка завершения курса при смене состояния.
 * Элементы без отслеживания (mode = none) игнорируются.
 */
@Component
public class CompletionTracker {

    private final CompletionRepository completions;
    private final CourseCompletionEvaluator courseCompletion;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public CompletionTracker(CompletionRepository completions, CourseCompletionEvaluator courseCompletion,
                             ApplicationEventPublisher events, Clock clock) {
        this.completions = completions;
        this.courseCompletion = courseCompletion;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public void update(ItemRef item, UUID userId, UnaryOperator<ItemCompletion> change) {
        CompletionRule rule = CompletionRule.of(item.completionMode(), item.completionTriggers());
        if (!rule.tracked()) {
            return;
        }
        Instant now = clock.instant();
        ItemCompletion before = completions.find(item.tenantId(), item.id(), userId)
                .orElse(ItemCompletion.empty(item.tenantId(), item.courseId(), item.id(), userId));
        ItemCompletion after = change.apply(before).evaluate(rule, now);
        if (after.equals(before)) {
            return;
        }
        completions.save(after, after.source(rule), now);
        if (after.complete() != before.complete()) {
            events.publishEvent(new ItemCompletionChanged(item.tenantId(), item.courseId(), item.id(), userId, after.complete()));
            courseCompletion.evaluate(item.tenantId(), item.courseId(), userId);
        }
    }
}
