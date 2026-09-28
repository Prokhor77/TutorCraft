package com.tutorcraft.core.progress.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.tutorcraft.core.progress.domain.ConditionGroup.Operator;
import com.tutorcraft.core.progress.domain.Reason.Code;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Движок условий доступа (FR-PROG-04): 100% покрытие ветвей ConditionEvaluator. */
class ConditionEvaluatorTest {

    private static final Instant NOW = Instant.parse("2026-09-27T10:00:00Z");
    private static final UUID TASK = UUID.fromString("00000000-0000-7000-8000-000000000001");
    private static final UUID GROUP = UUID.fromString("00000000-0000-7000-8000-0000000000aa");

    private static LearnerFacts facts(Set<UUID> completed, Map<UUID, Double> grades, Set<UUID> groups) {
        return new LearnerFacts(completed, grades, groups);
    }

    private static Evaluation evaluate(Operator operator, LearnerFacts facts, Condition... conditions) {
        return ConditionEvaluator.evaluate(new ConditionGroup(operator, true, List.of(conditions)), facts, NOW);
    }

    private static List<Code> codes(Evaluation evaluation) {
        return evaluation.reasons().stream().map(Reason::code).toList();
    }

    @Test
    void noConditionsMeansOpen() {
        assertThat(ConditionEvaluator.evaluate(null, LearnerFacts.none(), NOW)).isEqualTo(Evaluation.OPEN);
        assertThat(ConditionEvaluator.evaluate(new ConditionGroup(Operator.ALL, false, List.of()), LearnerFacts.none(), NOW))
                .isEqualTo(Evaluation.OPEN);
    }

    @Nested
    class Logic {

        private final Condition met = new Condition.Group(GROUP);
        private final Condition unmet = new Condition.Completion(TASK, true);
        private final LearnerFacts inGroup = facts(Set.of(), Map.of(), Set.of(GROUP));

        @Test
        void allRequiresEveryCondition() {
            assertThat(evaluate(Operator.ALL, inGroup, met).available()).isTrue();
            Evaluation locked = evaluate(Operator.ALL, inGroup, met, unmet);
            assertThat(locked.available()).isFalse();
            assertThat(codes(locked)).containsExactly(Code.COMPLETION_COMPLETE);
            assertThat(locked.operator()).isEqualTo(Operator.ALL);
        }

        @Test
        void anyRequiresOneConditionAndHidesReasonsWhenOpen() {
            Evaluation open = evaluate(Operator.ANY, inGroup, unmet, met);
            assertThat(open.available()).isTrue();
            assertThat(open.reasons()).isEmpty();
            Evaluation locked = evaluate(Operator.ANY, LearnerFacts.none(), unmet, met);
            assertThat(locked.available()).isFalse();
            assertThat(codes(locked)).containsExactly(Code.COMPLETION_COMPLETE, Code.GROUP);
        }

        @Test
        void showWhenLockedIsPropagated() {
            Evaluation hidden = ConditionEvaluator.evaluate(new ConditionGroup(Operator.ALL, false, List.of(unmet)),
                    LearnerFacts.none(), NOW);
            assertThat(hidden.showWhenLocked()).isFalse();
        }
    }

    @Nested
    class Dates {

        @Test
        void beforeFromIsLockedUntilOpening() {
            Instant from = NOW.plusSeconds(60);
            Evaluation evaluation = evaluate(Operator.ALL, LearnerFacts.none(), new Condition.DateWindow(from, null));
            assertThat(evaluation.reasons()).containsExactly(Reason.date(Code.DATE_FROM, from));
        }

        @Test
        void atOrAfterUntilIsClosed() {
            Evaluation evaluation = evaluate(Operator.ALL, LearnerFacts.none(), new Condition.DateWindow(null, NOW));
            assertThat(evaluation.reasons()).containsExactly(Reason.date(Code.DATE_UNTIL, NOW));
        }

        @Test
        void insideWindowIsOpen() {
            assertThat(evaluate(Operator.ALL, LearnerFacts.none(),
                    new Condition.DateWindow(NOW, NOW.plusSeconds(1))).available()).isTrue();
            assertThat(evaluate(Operator.ALL, LearnerFacts.none(), new Condition.DateWindow(null, NOW.plusSeconds(1))).available())
                    .isTrue();
            assertThat(evaluate(Operator.ALL, LearnerFacts.none(), new Condition.DateWindow(NOW.minusSeconds(1), null)).available())
                    .isTrue();
        }
    }

    @Nested
    class Completion {

        @Test
        void requireComplete() {
            assertThat(evaluate(Operator.ALL, facts(Set.of(TASK), Map.of(), Set.of()), new Condition.Completion(TASK, true))
                    .available()).isTrue();
            assertThat(codes(evaluate(Operator.ALL, LearnerFacts.none(), new Condition.Completion(TASK, true))))
                    .containsExactly(Code.COMPLETION_COMPLETE);
        }

        @Test
        void requireIncomplete() {
            assertThat(evaluate(Operator.ALL, LearnerFacts.none(), new Condition.Completion(TASK, false)).available()).isTrue();
            assertThat(codes(evaluate(Operator.ALL, facts(Set.of(TASK), Map.of(), Set.of()), new Condition.Completion(TASK, false))))
                    .containsExactly(Code.COMPLETION_INCOMPLETE);
        }
    }

    @Nested
    class Grades {

        private final LearnerFacts fifty = facts(Set.of(), Map.of(TASK, 50.0), Set.of());

        @Test
        void minimumIsInclusive() {
            assertThat(evaluate(Operator.ALL, fifty, new Condition.Grade(TASK, 50.0, null)).available()).isTrue();
            Evaluation locked = evaluate(Operator.ALL, fifty, new Condition.Grade(TASK, 60.0, null));
            assertThat(locked.reasons()).containsExactly(Reason.grade(Code.GRADE_MIN, TASK, 60.0, null, 50.0));
        }

        @Test
        void maximumIsExclusive() {
            assertThat(evaluate(Operator.ALL, fifty, new Condition.Grade(TASK, null, 60.0)).available()).isTrue();
            assertThat(codes(evaluate(Operator.ALL, fifty, new Condition.Grade(TASK, null, 50.0)))).containsExactly(Code.GRADE_MAX);
        }

        @Test
        void rangeUsesItsOwnCode() {
            assertThat(evaluate(Operator.ALL, fifty, new Condition.Grade(TASK, 40.0, 60.0)).available()).isTrue();
            assertThat(codes(evaluate(Operator.ALL, fifty, new Condition.Grade(TASK, 60.0, 80.0)))).containsExactly(Code.GRADE_RANGE);
        }

        @Test
        void missingGradeNeverSatisfiesBounds() {
            Evaluation minOnly = evaluate(Operator.ALL, LearnerFacts.none(), new Condition.Grade(TASK, 0.0, null));
            assertThat(minOnly.reasons()).containsExactly(Reason.grade(Code.GRADE_MIN, TASK, 0.0, null, null));
            assertThat(evaluate(Operator.ALL, LearnerFacts.none(), new Condition.Grade(TASK, null, 100.0)).available()).isFalse();
        }
    }

    @Nested
    class Groups {

        @Test
        void memberPassesOthersGetReason() {
            assertThat(evaluate(Operator.ALL, facts(Set.of(), Map.of(), Set.of(GROUP)), new Condition.Group(GROUP)).available())
                    .isTrue();
            assertThat(evaluate(Operator.ALL, LearnerFacts.none(), new Condition.Group(GROUP)).reasons())
                    .containsExactly(Reason.group(GROUP));
        }
    }
}
