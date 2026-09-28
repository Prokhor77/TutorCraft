package com.tutorcraft.core.courses.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.tutorcraft.core.courses.Availability;
import java.util.List;
import org.junit.jupiter.api.Test;

/** FR-PROG-03, UX-07: доступность вложенных элементов и режимы «скрыть»/«с замком». */
class AvailabilityRulesTest {

    private static final Availability LOCKED = new Availability(false, Availability.SHOW_LOCKED, List.of("after quiz"));
    private static final Availability HIDDEN = new Availability(false, Availability.HIDE, List.of("group only"));

    @Test
    void openOuterPassesInnerThrough() {
        assertThat(AvailabilityRules.combine(Availability.open(), LOCKED)).isEqualTo(LOCKED);
        assertThat(AvailabilityRules.combine(Availability.open(), Availability.open()).available()).isTrue();
    }

    @Test
    void lockedOuterLocksOpenInner() {
        assertThat(AvailabilityRules.combine(LOCKED, Availability.open())).isEqualTo(LOCKED);
    }

    @Test
    void bothLockedMergeReasonsAndHideWins() {
        Availability combined = AvailabilityRules.combine(LOCKED, HIDDEN);

        assertThat(combined.available()).isFalse();
        assertThat(combined.mode()).isEqualTo(Availability.HIDE);
        assertThat(combined.reasons()).containsExactly("after quiz", "group only");
    }

    @Test
    void duplicateReasonsAreMergedOnce() {
        assertThat(AvailabilityRules.combine(LOCKED, LOCKED).reasons()).containsExactly("after quiz");
    }

    @Test
    void nullMeansOpen() {
        assertThat(AvailabilityRules.combine(null, null).available()).isTrue();
    }

    @Test
    void onlyUnavailableHideModeIsHiddenFromLearner() {
        assertThat(AvailabilityRules.hiddenFromLearner(HIDDEN)).isTrue();
        assertThat(AvailabilityRules.hiddenFromLearner(LOCKED)).isFalse();
        assertThat(AvailabilityRules.hiddenFromLearner(new Availability(true, Availability.HIDE, List.of()))).isFalse();
        assertThat(AvailabilityRules.hiddenFromLearner(null)).isFalse();
    }
}
