package com.tutorcraft.core.progress.domain;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Доступность модулей и элементов курса: модуль недоступен, если недоступен его родитель; элемент недоступен,
 * если недоступен его модуль (тогда причины и режим показа — модуля), иначе — по собственным условиям.
 */
public final class AvailabilityTree {

    private AvailabilityTree() {
    }

    /** Узел дерева: для модуля {@code parentId} — родительский модуль, для элемента — модуль. */
    public record Node(UUID id, UUID parentId, ConditionGroup conditions) {
    }

    public static Map<UUID, Evaluation> evaluate(List<Node> modules, List<Node> items, LearnerFacts facts, Instant now) {
        Map<UUID, Node> byId = modules.stream().collect(Collectors.toMap(Node::id, Function.identity(), (a, b) -> a));
        Map<UUID, Evaluation> result = new HashMap<>();
        modules.forEach(module -> moduleEvaluation(module, byId, facts, now, result, new HashSet<>()));
        for (Node item : items) {
            Evaluation parent = item.parentId() == null ? Evaluation.OPEN : result.getOrDefault(item.parentId(), Evaluation.OPEN);
            result.put(item.id(), parent.available() ? ConditionEvaluator.evaluate(item.conditions(), facts, now) : parent);
        }
        return result;
    }

    private static Evaluation moduleEvaluation(Node module, Map<UUID, Node> byId, LearnerFacts facts, Instant now,
                                               Map<UUID, Evaluation> memo, Set<UUID> path) {
        Evaluation known = memo.get(module.id());
        if (known != null) {
            return known;
        }
        Node parent = module.parentId() == null ? null : byId.get(module.parentId());
        Evaluation parentEvaluation = parent == null || !path.add(module.id())
                ? Evaluation.OPEN : moduleEvaluation(parent, byId, facts, now, memo, path);
        Evaluation evaluation = parentEvaluation.available()
                ? ConditionEvaluator.evaluate(module.conditions(), facts, now) : parentEvaluation;
        memo.put(module.id(), evaluation);
        return evaluation;
    }
}
