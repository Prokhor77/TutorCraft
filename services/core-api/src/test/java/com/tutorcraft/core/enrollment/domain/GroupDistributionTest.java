package com.tutorcraft.core.enrollment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tutorcraft.core.enrollment.domain.GroupDistribution.Strategy;
import com.tutorcraft.core.shared.domain.ValidationException;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** FR-ENROL-05: автоматическое распределение по группам. */
class GroupDistributionTest {

    private static List<UUID> members(int count) {
        return IntStream.range(0, count).mapToObj(i -> UUID.randomUUID()).toList();
    }

    @ParameterizedTest
    @CsvSource({"BY_COUNT,10,3,3", "BY_COUNT,2,5,2", "BY_SIZE,10,3,4", "BY_SIZE,9,3,3", "BY_SIZE,1,5,1"})
    void createsExpectedNumberOfGroups(Strategy strategy, int memberCount, int value, int expectedGroups) {
        List<List<UUID>> groups = GroupDistribution.distribute(members(memberCount), strategy, value, new Random(7));

        assertThat(groups).hasSize(expectedGroups);
    }

    @Test
    void everyMemberIsPlacedExactlyOnceAndSizesAreBalanced() {
        List<UUID> members = members(23);

        List<List<UUID>> groups = GroupDistribution.distribute(members, Strategy.BY_COUNT, 5, new Random(42));

        assertThat(groups.stream().flatMap(Collection::stream).toList()).containsExactlyInAnyOrderElementsOf(members);
        int min = groups.stream().mapToInt(List::size).min().orElseThrow();
        int max = groups.stream().mapToInt(List::size).max().orElseThrow();
        assertThat(max - min).isLessThanOrEqualTo(1);
    }

    @Test
    void bySizeNeverExceedsSize() {
        List<List<UUID>> groups = GroupDistribution.distribute(members(17), Strategy.BY_SIZE, 4, new Random(1));
        assertThat(groups).allSatisfy(group -> assertThat(group).hasSizeLessThanOrEqualTo(4));
    }

    @Test
    void distributionIsRandomized() {
        List<UUID> members = members(20);

        List<List<UUID>> first = GroupDistribution.distribute(members, Strategy.BY_COUNT, 2, new Random(1));
        List<List<UUID>> second = GroupDistribution.distribute(members, Strategy.BY_COUNT, 2, new Random(2));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void emptyCourseProducesNoGroups() {
        assertThat(GroupDistribution.distribute(List.of(), Strategy.BY_SIZE, 3, new Random())).isEmpty();
    }

    @Test
    void valueOutOfRangeIsRejected() {
        assertThatThrownBy(() -> GroupDistribution.distribute(members(3), Strategy.BY_SIZE, 0, new Random()))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> GroupDistribution.distribute(members(3), Strategy.BY_COUNT, GroupDistribution.MAX_VALUE + 1,
                new Random())).isInstanceOf(ValidationException.class);
    }

    @Test
    void strategyKeys() {
        assertThat(Strategy.fromKey("by_count")).isEqualTo(Strategy.BY_COUNT);
        assertThatThrownBy(() -> Strategy.fromKey("alphabetical")).isInstanceOf(ValidationException.class);
    }

    @Test
    void autoGroupNamesSkipTakenOnes() {
        assertThat(GroupNames.next("Группа", Set.of("группа 1", "Группа 3"), 3))
                .containsExactly("Группа 2", "Группа 4", "Группа 5");
    }
}
