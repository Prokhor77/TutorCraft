package com.tutorcraft.core.enrollment.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Имена автоматически созданных групп: «Префикс N», пропуская уже занятые (без учёта регистра). */
public final class GroupNames {

    private static final int FIRST_NUMBER = 1;

    private GroupNames() {
    }

    public static List<String> next(String prefix, Set<String> existing, int count) {
        Set<String> taken = existing.stream().map(name -> name.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        List<String> result = new ArrayList<>(count);
        for (int n = FIRST_NUMBER; result.size() < count; n++) {
            String candidate = prefix + " " + n;
            if (!taken.contains(candidate.toLowerCase(Locale.ROOT))) {
                result.add(candidate);
            }
        }
        return result;
    }
}
