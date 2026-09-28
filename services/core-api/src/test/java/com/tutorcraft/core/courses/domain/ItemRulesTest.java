package com.tutorcraft.core.courses.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tutorcraft.core.shared.domain.ValidationException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Форма правил выполнения (контракт §5). Условия доступа — ConditionRulesTest / progress ConditionParserTest. */
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
}
