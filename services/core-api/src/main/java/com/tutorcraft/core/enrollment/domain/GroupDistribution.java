package com.tutorcraft.core.enrollment.domain;

import com.tutorcraft.core.shared.domain.ValidationException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Автоматическое распределение по группам (FR-ENROL-05): случайно по N групп (by_count) или по N человек (by_size).
 * Размеры групп отличаются не более чем на 1; случайность передаётся снаружи (в проде — SecureRandom).
 */
public final class GroupDistribution {

    public static final int MAX_VALUE = 1000;

    private GroupDistribution() {
    }

    public enum Strategy {
        BY_COUNT("by_count"), BY_SIZE("by_size");

        private final String key;

        Strategy(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        public static Strategy fromKey(String key) {
            return Arrays.stream(values()).filter(strategy -> strategy.key.equals(key)).findFirst()
                    .orElseThrow(() -> ValidationException.single("strategy", "invalid", "Unknown distribution strategy"));
        }
    }

    public static List<List<UUID>> distribute(List<UUID> members, Strategy strategy, int value, Random random) {
        if (value < 1 || value > MAX_VALUE) {
            throw ValidationException.single("value", "out_of_range", "value must be between 1 and " + MAX_VALUE);
        }
        if (members.isEmpty()) {
            return List.of();
        }
        List<UUID> shuffled = new ArrayList<>(members);
        Collections.shuffle(shuffled, random);
        int groupCount = groupCount(shuffled.size(), strategy, value);
        List<List<UUID>> groups = new ArrayList<>(groupCount);
        for (int g = 0; g < groupCount; g++) {
            groups.add(new ArrayList<>());
        }
        for (int i = 0; i < shuffled.size(); i++) {
            groups.get(i % groupCount).add(shuffled.get(i));
        }
        return groups.stream().map(List::copyOf).toList();
    }

    /** by_count — не больше групп, чем участников; by_size — ceil(n / size). */
    static int groupCount(int members, Strategy strategy, int value) {
        return switch (strategy) {
            case BY_COUNT -> Math.min(value, members);
            case BY_SIZE -> (members + value - 1) / value;
        };
    }
}
