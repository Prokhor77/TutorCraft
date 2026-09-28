package com.tutorcraft.core.progress.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.tutorcraft.core.progress.ConditionSchema;
import com.tutorcraft.core.progress.domain.ConditionGroup.Operator;
import com.tutorcraft.core.shared.domain.ValidationException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Схема условий доступа (контракт §5 ConditionGroup). */
class ConditionParserTest {

    private static final String ITEM = "0192f3c1-0000-7000-8000-000000000001";

    private static List<String> violations(Map<String, Object> raw) {
        try {
            ConditionParser.parse(raw);
        } catch (ValidationException e) {
            return e.violations().stream().map(v -> v.field() + ":" + v.code()).toList();
        }
        throw new AssertionError("ValidationException expected");
    }

    @Test
    void parsesAllConditionTypesAndRoundTrips() {
        Map<String, Object> raw = Map.of("op", "any", "showWhenLocked", false, "conditions", List.of(
                Map.of("type", "date", "from", "2026-10-01T00:00:00Z"),
                Map.of("type", "completion", "itemId", ITEM, "state", "incomplete"),
                Map.of("type", "grade", "itemId", ITEM, "minPercent", 60),
                Map.of("type", "group", "groupId", ITEM)));
        ConditionGroup group = ConditionParser.parse(raw);
        assertThat(group.operator()).isEqualTo(Operator.ANY);
        assertThat(group.showWhenLocked()).isFalse();
        assertThat(group.conditions()).containsExactly(
                new Condition.DateWindow(Instant.parse("2026-10-01T00:00:00Z"), null),
                new Condition.Completion(UUID.fromString(ITEM), false),
                new Condition.Grade(UUID.fromString(ITEM), 60.0, null),
                new Condition.Group(UUID.fromString(ITEM)));
        assertThat(ConditionParser.parse(ConditionParser.toMap(group))).isEqualTo(group);
    }

    @Test
    void emptyInputMeansNoConditions() {
        assertThat(ConditionParser.parse(null)).isNull();
        assertThat(ConditionSchema.validate(Map.of())).isNull();
    }

    @Test
    void reportsInvalidConditions() {
        Map<String, Object> raw = Map.of("conditions", List.of(
                Map.of("type", "date"),
                Map.of("type", "grade", "itemId", ITEM, "minPercent", 80, "maxPercent", 120)));
        assertThat(violations(raw)).containsExactlyInAnyOrder("conditions.conditions[0]:bound_required",
                "conditions.conditions[1].maxPercent:out_of_range");
        assertThat(violations(Map.of("conditions", List.of(Map.of("type", "grade", "itemId", ITEM, "minPercent", 70,
                "maxPercent", 60))))).containsExactly("conditions.conditions[0].maxPercent:not_greater");
    }

    @Test
    void rejectsUnknownTypeOperatorAndBadIds() {
        assertThat(violations(Map.of("conditions", List.of(Map.of("type", "moon"))))).containsExactly("conditions.conditions[0].type:invalid");
        assertThat(violations(Map.of("op", "xor", "conditions", List.of()))).containsExactly("conditions.op:invalid");
        assertThat(violations(Map.of("conditions", List.of(Map.of("type", "completion", "itemId", "nope")))))
                .containsExactly("conditions.conditions[0].itemId:invalid_uuid");
        assertThat(violations(Map.of("op", "all"))).containsExactly("conditions.conditions:required");
    }

    @Test
    void schemaListsReferencedItems() {
        Map<String, Object> raw = Map.of("conditions", List.of(Map.of("type", "completion", "itemId", ITEM),
                Map.of("type", "group", "groupId", ITEM)));
        assertThat(ConditionSchema.referencedItemIds(raw)).containsExactly(UUID.fromString(ITEM));
        assertThat(ConditionSchema.validate(raw)).containsEntry("op", "all");
    }
}
