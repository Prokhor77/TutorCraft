package com.tutorcraft.core.courses.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tutorcraft.core.courses.ItemType;
import com.tutorcraft.core.courses.Visibility;
import com.tutorcraft.core.courses.domain.CourseItem;
import com.tutorcraft.core.shared.domain.ValidationException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** FR-PROG-02: условия доступа проверяются схемой progress и ссылаются только на элементы своего курса. */
class ConditionRulesTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID COURSE = UUID.randomUUID();
    private static final UUID OTHER_COURSE = UUID.randomUUID();

    private final ItemRepository items = mock(ItemRepository.class);
    private final ConditionRules rules = new ConditionRules(items);

    @Test
    void normalizesValidTreeAndEmptyMeansNoConditions() {
        CourseItem target = item(COURSE);
        CourseItem dependency = item(COURSE);
        when(items.findAll(eq(TENANT), any())).thenReturn(Map.of(dependency.id(), dependency));

        Map<String, Object> normalized = rules.forItem(target, completionOf(dependency.id()));

        assertThat(normalized).containsEntry("op", "all").containsEntry("showWhenLocked", true);
        assertThat(rules.forModule(TENANT, COURSE, Map.of())).isNull();
        assertThat(rules.forModule(TENANT, COURSE, null)).isNull();
    }

    @Test
    void rejectsItemOfAnotherCourseOrMissingItem() {
        CourseItem foreign = item(OTHER_COURSE);
        when(items.findAll(eq(TENANT), any())).thenReturn(Map.of(foreign.id(), foreign));

        assertThatThrownBy(() -> rules.forModule(TENANT, COURSE, completionOf(foreign.id())))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).violations().get(0).code()).isEqualTo("item_not_in_course"));
        assertThatThrownBy(() -> rules.forModule(TENANT, COURSE, completionOf(UUID.randomUUID())))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsSelfReference() {
        CourseItem target = item(COURSE);

        assertThatThrownBy(() -> rules.forItem(target, completionOf(target.id())))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).violations().get(0).code()).isEqualTo("self_reference"));
    }

    @Test
    void rejectsMalformedTreeViaProgressSchema() {
        assertThatThrownBy(() -> rules.forModule(TENANT, COURSE, Map.of("op", "xor", "conditions", List.of())))
                .isInstanceOf(ValidationException.class);
    }

    private static Map<String, Object> completionOf(UUID itemId) {
        return Map.of("op", "all", "conditions", List.of(Map.of("type", "completion", "itemId", itemId.toString(), "state", "complete")));
    }

    private static CourseItem item(UUID courseId) {
        Instant now = Instant.parse("2026-09-01T00:00:00Z");
        return new CourseItem(UUID.randomUUID(), TENANT, courseId, UUID.randomUUID(), ItemType.PAGE, "Page", 0, Visibility.PUBLISHED,
                null, Map.of(), null, Map.of("mode", "none"), null, new CourseItem.KeyDates(null, null, null), 1, null, now, now);
    }
}
