package com.tutorcraft.core.gradebook.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GradeTest {

    private static final Instant T1 = Instant.parse("2026-09-01T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-09-02T10:00:00Z");
    private static final UUID GRADER = UUID.randomUUID();

    private final Grade empty = Grade.empty(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    @Test
    void sourceScoreIsPublishedOnlyOnceAndKeepsFirstPublicationTime() {
        Grade draft = empty.withSourceScore(BigDecimal.TEN, GRADER, T1, false);
        assertThat(draft.published()).isFalse();

        Grade published = draft.withSourceScore(BigDecimal.ONE, GRADER, T1, true);
        Grade regraded = published.withSourceScore(BigDecimal.TWO, GRADER, T2, true);

        assertThat(published.publishedAt()).isEqualTo(T1);
        assertThat(regraded.publishedAt()).isEqualTo(T1);
        assertThat(regraded.finalScore()).isEqualByComparingTo("2");
    }

    @Test
    void overriddenOrLockedGradesRejectSourceScores() {
        assertThat(empty.acceptsSourceScore()).isTrue();
        assertThat(empty.withOverride(BigDecimal.ONE, false, GRADER, T1).acceptsSourceScore()).isFalse();
        Grade locked = new Grade(empty.id(), empty.tenantId(), empty.columnId(), empty.userId(), null, null, false, true,
                null, null, null, 0);
        assertThat(locked.acceptsSourceScore()).isFalse();
    }

    @Test
    void overrideKeepsRawScoreAndSetsLock() {
        Grade source = empty.withSourceScore(BigDecimal.valueOf(40), GRADER, T1, true);
        Grade overridden = source.withOverride(BigDecimal.valueOf(55), true, GRADER, T2);

        assertThat(overridden.rawScore()).isEqualByComparingTo("40");
        assertThat(overridden.finalScore()).isEqualByComparingTo("55");
        assertThat(overridden.overridden()).isTrue();
        assertThat(overridden.locked()).isTrue();
    }

    @Test
    void manualScoreIsPublishedImmediately() {
        Grade manual = empty.withManualScore(BigDecimal.valueOf(3), GRADER, T1);

        assertThat(manual.published()).isTrue();
        assertThat(manual.overridden()).isFalse();
        assertThat(manual.publishedAt(T2).publishedAt()).isEqualTo(T2);
    }

    @Test
    void scoreDifferenceIgnoresScale() {
        Grade a = empty.withSourceScore(new BigDecimal("5.0"), GRADER, T1, false);
        Grade b = empty.withSourceScore(new BigDecimal("5.00"), GRADER, T1, false);

        assertThat(a.scoreDiffers(b)).isFalse();
        assertThat(a.scoreDiffers(empty)).isTrue();
        assertThat(empty.scoreDiffers(empty)).isFalse();
    }
}
