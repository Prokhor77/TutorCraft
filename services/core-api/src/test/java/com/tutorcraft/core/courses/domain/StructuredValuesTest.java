package com.tutorcraft.core.courses.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Слияние настроек (DATA-04) и переназначение ссылок при дублировании (FR-COURSE-06). */
class StructuredValuesTest {

    @Test
    void mergeOverridesAndClearsKeys() {
        Map<String, Object> current = Map.of("maxScore", 100, "dueAt", "2026-10-01T00:00:00Z");
        Map<String, Object> patch = new HashMap<>();
        patch.put("maxScore", 50);
        patch.put("dueAt", null);

        Map<String, Object> merged = StructuredValues.merge(current, patch);

        assertThat(merged).containsEntry("maxScore", 50).containsEntry("dueAt", null);
    }

    @Test
    void mergeToleratesNulls() {
        assertThat(StructuredValues.merge(null, null)).isEmpty();
        assertThat(StructuredValues.merge(Map.of("a", 1), null)).containsEntry("a", 1);
    }

    @Test
    void remapReplacesIdsDeepAndKeepsOthers() {
        UUID old = UUID.randomUUID();
        UUID fresh = UUID.randomUUID();
        UUID untouched = UUID.randomUUID();
        Map<String, Object> conditions = Map.of("op", "all", "conditions", List.of(
                Map.of("type", "completion", "itemId", old.toString()),
                Map.of("type", "grade", "itemId", untouched.toString(), "minPercent", 60)));

        Map<String, Object> remapped = StructuredValues.remapMap(conditions, Map.of(old, fresh));

        assertThat(remapped.toString()).contains(fresh.toString(), untouched.toString()).doesNotContain(old.toString());
        assertThat(remapped.get("op")).isEqualTo("all");
    }

    @Test
    void deepCopyIsMutableAndIndependent() {
        Map<String, Object> source = Map.of("list", List.of(Map.of("k", "v")));

        Map<String, Object> copy = StructuredValues.copyMap(source);
        copy.put("extra", true);

        assertThat(source).doesNotContainKey("extra");
        assertThat(StructuredValues.copyMap(null)).isNull();
    }
}
