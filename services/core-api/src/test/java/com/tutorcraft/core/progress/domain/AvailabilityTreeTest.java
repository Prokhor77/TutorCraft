package com.tutorcraft.core.progress.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.tutorcraft.core.progress.domain.AvailabilityTree.Node;
import com.tutorcraft.core.progress.domain.ConditionGroup.Operator;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Недоступность модуля распространяется на вложенные модули и элементы (FR-PROG-02/03). */
class AvailabilityTreeTest {

    private static final Instant NOW = Instant.parse("2026-09-27T10:00:00Z");
    private static final UUID PARENT = new UUID(0, 1);
    private static final UUID CHILD = new UUID(0, 2);
    private static final UUID ITEM = new UUID(0, 3);
    private static final UUID OTHER_ITEM = new UUID(0, 4);
    private static final UUID FREE_MODULE = new UUID(0, 5);

    private static ConditionGroup notBefore(Instant from, boolean show) {
        return new ConditionGroup(Operator.ALL, show, List.of(new Condition.DateWindow(from, null)));
    }

    @Test
    void lockedParentLocksChildrenAndTheirItems() {
        List<Node> modules = List.of(new Node(CHILD, PARENT, null), new Node(PARENT, null, notBefore(NOW.plusSeconds(60), false)),
                new Node(FREE_MODULE, null, null));
        List<Node> items = List.of(new Node(ITEM, CHILD, null), new Node(OTHER_ITEM, FREE_MODULE, null));
        Map<UUID, Evaluation> result = AvailabilityTree.evaluate(modules, items, LearnerFacts.none(), NOW);
        assertThat(result.get(PARENT).available()).isFalse();
        assertThat(result.get(CHILD)).isEqualTo(result.get(PARENT));
        assertThat(result.get(ITEM)).isEqualTo(result.get(PARENT));
        assertThat(result.get(ITEM).showWhenLocked()).isFalse();
        assertThat(result.get(OTHER_ITEM).available()).isTrue();
    }

    @Test
    void itemConditionsApplyInsideOpenModule() {
        List<Node> items = List.of(new Node(ITEM, FREE_MODULE, notBefore(NOW.plusSeconds(1), true)), new Node(OTHER_ITEM, null, null));
        Map<UUID, Evaluation> result = AvailabilityTree.evaluate(List.of(new Node(FREE_MODULE, null, null)), items,
                LearnerFacts.none(), NOW);
        assertThat(result.get(ITEM).available()).isFalse();
        assertThat(result.get(OTHER_ITEM).available()).isTrue();
    }

    @Test
    void cyclicModulesDoNotLoop() {
        List<Node> modules = List.of(new Node(PARENT, CHILD, null), new Node(CHILD, PARENT, null));
        Map<UUID, Evaluation> result = AvailabilityTree.evaluate(modules, List.of(), LearnerFacts.none(), NOW);
        assertThat(result.get(PARENT).available()).isTrue();
        assertThat(result.get(CHILD).available()).isTrue();
    }
}
