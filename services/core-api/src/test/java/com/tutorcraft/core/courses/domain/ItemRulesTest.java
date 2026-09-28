package com.tutorcraft.core.courses.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tutorcraft.core.shared.domain.ValidationException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Форма правил выполнения и условий доступа (контракт §5). */
class ItemRulesTest {

    @Test
    void nullCompletionRuleMeansNone() {
        assertThat(ItemRules.completionRule(null, "completionRule")).containsEntry("mode", "none");
    }

    @Test
    void acceptsAutoWithTriggers() {
        Map<String, Object> rule = ItemRules.completionRule(Map.of("mode", "auto", "on", List.of("submitted", "graded")),
                "completionRule");
        assertThat(rule).containsEntry("mode", "auto").containsEntry("on", List.of("submitted", "graded"));
    }

    @Test
    void rejectsUnknownModeOrTrigger() {
        assertThatThrownBy(() -> ItemRules.completionRule(Map.of("mode", "sometimes"), "completionRule"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> ItemRules.completionRule(Map.of("mode", "auto", "on", List.of("liked")), "completionRule"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void acceptsWellFormedConditionGroup() {
        Map<String, Object> group = Map.of("op", "all", "showWhenLocked", true, "conditions", List.of(
                Map.of("type", "date", "from", "2026-10-01T00:00:00Z"),
                Map.of("type", "completion", "itemId", UUID.randomUUID().toString(), "state", "complete")));

        assertThat(ItemRules.conditions(group, "conditions")).containsEntry("op", "all");
        assertThat(ItemRules.conditions(null, "conditions")).isNull();
    }

    @Test
    void rejectsMalformedConditions() {
        assertThatThrownBy(() -> ItemRules.conditions(Map.of("op", "xor", "conditions", List.of()), "conditions"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> ItemRules.conditions(Map.of("op", "any", "conditions", List.of(Map.of("type", "moon"))),
                "conditions")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> ItemRules.conditions(Map.of("op", "any", "conditions",
                List.of(Map.of("type", "completion", "itemId", "not-a-uuid"))), "conditions"))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).violations().get(0).field())
                        .isEqualTo("conditions.conditions[0].itemId"));
    }
}
