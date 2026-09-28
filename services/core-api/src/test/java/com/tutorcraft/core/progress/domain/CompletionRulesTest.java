package com.tutorcraft.core.progress.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Выполнение элемента (FR-PROG-01) и завершение курса (FR-PROG-05). */
class CompletionRulesTest {

    private static final Instant NOW = Instant.parse("2026-09-27T10:00:00Z");
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);

    private static ItemCompletion empty() {
        return ItemCompletion.empty(new UUID(1, 1), new UUID(1, 2), A, new UUID(1, 3));
    }

    @Test
    void autoRuleNeedsEveryTrigger() {
        CompletionRule rule = CompletionRule.of("auto", List.of("viewed", "passed", "unknown"));
        assertThat(rule.on()).containsExactlyInAnyOrder(CompletionTrigger.VIEWED, CompletionTrigger.PASSED);
        ItemCompletion viewed = empty().withTrigger(CompletionTrigger.VIEWED, true).evaluate(rule, NOW);
        assertThat(viewed.complete()).isFalse();
        ItemCompletion passed = viewed.withTrigger(CompletionTrigger.PASSED, true).evaluate(rule, NOW);
        assertThat(passed.complete()).isTrue();
        assertThat(passed.completedAt()).isEqualTo(NOW);
        assertThat(passed.source(rule)).isEqualTo(ItemCompletion.SOURCE_AUTO);
    }

    @Test
    void completionDateIsKeptAndClearedWhenRevoked() {
        CompletionRule rule = CompletionRule.of("auto", List.of("passed"));
        ItemCompletion done = empty().withTrigger(CompletionTrigger.PASSED, true).evaluate(rule, NOW);
        ItemCompletion later = done.withTrigger(CompletionTrigger.VIEWED, true).evaluate(rule, NOW.plusSeconds(60));
        assertThat(later.completedAt()).isEqualTo(NOW);
        ItemCompletion revoked = later.withTrigger(CompletionTrigger.PASSED, false).evaluate(rule, NOW.plusSeconds(120));
        assertThat(revoked.complete()).isFalse();
        assertThat(revoked.completedAt()).isNull();
    }

    @Test
    void manualAndNoneModes() {
        CompletionRule manual = CompletionRule.of("manual", List.of());
        assertThat(empty().withManualMark(true).evaluate(manual, NOW).complete()).isTrue();
        assertThat(manual.isComplete(Set.of(CompletionTrigger.VIEWED), false)).isFalse();
        assertThat(empty().source(manual)).isEqualTo(ItemCompletion.SOURCE_MANUAL);
        CompletionRule none = CompletionRule.of(null, List.of("viewed"));
        assertThat(none.tracked()).isFalse();
        assertThat(none.isComplete(Set.of(CompletionTrigger.VIEWED), true)).isFalse();
        assertThat(CompletionRule.of("auto", List.of()).isComplete(Set.of(), false)).isFalse();
    }

    @Test
    void courseCompletionByItemsAndOrFinalGrade() {
        assertThat(CourseCompletionPolicy.isComplete(List.of(), null, Set.of(A), 100.0)).isFalse();
        assertThat(CourseCompletionPolicy.isComplete(List.of(A, B), null, Set.of(A), null)).isFalse();
        assertThat(CourseCompletionPolicy.isComplete(List.of(A, B), null, Set.of(A, B), null)).isTrue();
        assertThat(CourseCompletionPolicy.isComplete(List.of(), 70.0, Set.of(), 69.9)).isFalse();
        assertThat(CourseCompletionPolicy.isComplete(List.of(), 70.0, Set.of(), null)).isFalse();
        assertThat(CourseCompletionPolicy.isComplete(List.of(A), 70.0, Set.of(A), 70.0)).isTrue();
        assertThat(CourseCompletionPolicy.isComplete(List.of(A), 70.0, Set.of(), 90.0)).isFalse();
    }

    @Test
    void percentAndPassThreshold() {
        assertThat(ProgressMath.percent(List.of(), Set.of())).isNull();
        assertThat(ProgressMath.percent(List.of(A, B, new UUID(0, 3)), Set.of(A))).isEqualTo(33);
        assertThat(ProgressMath.passed(50.0, null)).isTrue();
        assertThat(ProgressMath.passed(49.9, null)).isFalse();
        assertThat(ProgressMath.passed(59.0, 60.0)).isFalse();
        assertThat(ProgressMath.passed(null, 0.0)).isFalse();
    }
}
