package com.tutorcraft.core.assessment.quiz.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Детерминированное перемешивание: одинаковый seed (попытка + слот) даёт одинаковый порядок,
 * поэтому перезагрузка страницы не меняет порядок вариантов. {@link Random} специфицирован JLS и стабилен между JVM.
 */
public final class SeededShuffle {

    private static final long SLOT_MULTIPLIER = 0x9E3779B97F4A7C15L;

    private SeededShuffle() {
    }

    public static long seed(UUID attemptId, int slot) {
        return attemptId.getMostSignificantBits() ^ attemptId.getLeastSignificantBits() ^ (slot * SLOT_MULTIPLIER);
    }

    public static <T> List<T> shuffle(List<T> source, long seed) {
        List<T> copy = new ArrayList<>(source);
        Collections.shuffle(copy, new Random(seed));
        return List.copyOf(copy);
    }

    /** Перемешивание, гарантированно отличное от исходного порядка (если элементов больше одного и они различимы). */
    public static <T> List<T> derange(List<T> source, long seed) {
        List<T> shuffled = shuffle(source, seed);
        if (shuffled.size() < 2 || !shuffled.equals(source)) {
            return shuffled;
        }
        List<T> rotated = new ArrayList<>(shuffled.subList(1, shuffled.size()));
        rotated.add(shuffled.get(0));
        return List.copyOf(rotated);
    }
}
