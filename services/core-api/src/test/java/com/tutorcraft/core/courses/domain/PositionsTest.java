package com.tutorcraft.core.courses.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** FR-COURSE-03: перенумерация при drag&drop — плотные позиции, пишутся только изменения. */
class PositionsTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();
    private final UUID d = UUID.randomUUID();

    @Test
    void movesUpWithinList() {
        assertThat(Positions.insert(List.of(a, b, c), c, 0)).containsExactly(c, a, b);
    }

    @Test
    void movesDownWithinList() {
        assertThat(Positions.insert(List.of(a, b, c), a, 2)).containsExactly(b, c, a);
    }

    @Test
    void insertsNewElementAndClampsPosition() {
        assertThat(Positions.insert(List.of(a, b), d, 1)).containsExactly(a, d, b);
        assertThat(Positions.insert(List.of(a, b), d, 100)).containsExactly(a, b, d);
        assertThat(Positions.insert(List.of(a, b), d, -5)).containsExactly(d, a, b);
        assertThat(Positions.insert(List.of(), d, 3)).containsExactly(d);
    }

    @Test
    void removeClosesGap() {
        assertThat(Positions.remove(List.of(a, b, c), b)).containsExactly(a, c);
        assertThat(Positions.remove(List.of(a), d)).containsExactly(a);
    }

    @Test
    void changesContainOnlyMovedElements() {
        Map<UUID, Integer> current = Map.of(a, 0, b, 1, c, 2, d, 3);

        Map<UUID, Integer> changes = Positions.changes(List.of(a, c, b, d), current);

        assertThat(changes).containsExactlyInAnyOrderEntriesOf(Map.of(c, 1, b, 2));
    }

    @Test
    void changesRepairNonDenseStoredPositions() {
        Map<UUID, Integer> current = Map.of(a, 0, b, 5, c, 9);

        assertThat(Positions.changes(List.of(a, b, c), current)).containsExactlyInAnyOrderEntriesOf(Map.of(b, 1, c, 2));
    }

    @Test
    void newElementAppearsInChanges() {
        assertThat(Positions.changes(List.of(d, a), Map.of(a, 0))).containsExactlyInAnyOrderEntriesOf(Map.of(d, 0, a, 1));
    }

    @Test
    void clampBounds() {
        assertThat(Positions.clamp(-1, 3)).isZero();
        assertThat(Positions.clamp(2, 3)).isEqualTo(2);
        assertThat(Positions.clamp(7, 3)).isEqualTo(3);
    }
}
