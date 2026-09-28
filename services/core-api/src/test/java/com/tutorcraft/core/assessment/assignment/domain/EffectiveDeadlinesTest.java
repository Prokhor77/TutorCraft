package com.tutorcraft.core.assessment.assignment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Действующий срок = настройки ⊕ продление (FR-ASSIGN-03). */
class EffectiveDeadlinesTest {

    private static final Instant OPEN = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant DUE = Instant.parse("2026-09-10T00:00:00Z");
    private static final Instant CLOSE = Instant.parse("2026-09-12T00:00:00Z");

    @Test
    void withoutExtensionSettingsApply() {
        EffectiveDeadlines deadlines = EffectiveDeadlines.of(settings(CLOSE), List.of());

        assertThat(deadlines).isEqualTo(new EffectiveDeadlines(OPEN, DUE, CLOSE));
    }

    @Test
    void personalExtensionWinsOverGroupExtensions() {
        Instant personalDue = DUE.plusSeconds(3600);
        ItemOverride group = groupExtension(DUE.plusSeconds(7200), null);
        ItemOverride personal = userExtension(personalDue, null);

        EffectiveDeadlines deadlines = EffectiveDeadlines.of(settings(CLOSE), List.of(group, personal));

        assertThat(deadlines.dueAt()).isEqualTo(personalDue);
    }

    @Test
    void latestGroupExtensionApplies() {
        Instant later = DUE.plusSeconds(86_400);
        EffectiveDeadlines deadlines = EffectiveDeadlines.of(settings(null),
                List.of(groupExtension(DUE.plusSeconds(60), null), groupExtension(later, null)));

        assertThat(deadlines.dueAt()).isEqualTo(later);
        assertThat(deadlines.closeAt()).isNull();
    }

    @Test
    void extensionCloseAtOverridesHardClose() {
        Instant extensionClose = CLOSE.plusSeconds(86_400 * 5L);
        EffectiveDeadlines deadlines = EffectiveDeadlines.of(settings(CLOSE),
                List.of(userExtension(CLOSE.plusSeconds(86_400), extensionClose)));

        assertThat(deadlines.closeAt()).isEqualTo(extensionClose);
    }

    @Test
    void hardCloseMovesToExtendedDueWhenExtensionHasNoClose() {
        Instant extendedDue = CLOSE.plusSeconds(86_400);
        EffectiveDeadlines deadlines = EffectiveDeadlines.of(settings(CLOSE), List.of(userExtension(extendedDue, null)));

        assertThat(deadlines.closeAt()).isEqualTo(extendedDue);
    }

    @Test
    void hardCloseAfterExtendedDueIsKept() {
        EffectiveDeadlines deadlines = EffectiveDeadlines.of(settings(CLOSE), List.of(userExtension(DUE.plusSeconds(60), null)));

        assertThat(deadlines.closeAt()).isEqualTo(CLOSE);
        assertThat(deadlines.openAt()).isEqualTo(OPEN);
    }

    @Test
    void overridesWithoutDueDateAreIgnored() {
        EffectiveDeadlines deadlines = EffectiveDeadlines.of(settings(CLOSE), List.of(userExtension(null, CLOSE.plusSeconds(1))));

        assertThat(deadlines).isEqualTo(new EffectiveDeadlines(OPEN, DUE, CLOSE));
    }

    private static AssignmentSettings settings(Instant closeAt) {
        return new AssignmentSettings(SubmissionType.FILE, BigDecimal.TEN, DUE, OPEN, closeAt, List.of(), 5, 50, null, false,
                true, null, true);
    }

    private static ItemOverride userExtension(Instant dueAt, Instant closeAt) {
        return new ItemOverride(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                null, dueAt, closeAt, UUID.randomUUID(), OPEN);
    }

    private static ItemOverride groupExtension(Instant dueAt, Instant closeAt) {
        return new ItemOverride(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                UUID.randomUUID(), dueAt, closeAt, UUID.randomUUID(), OPEN);
    }
}
