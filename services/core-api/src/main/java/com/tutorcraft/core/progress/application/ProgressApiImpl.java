package com.tutorcraft.core.progress.application;

import com.tutorcraft.core.courses.CoursesApi;
import com.tutorcraft.core.courses.ItemRef;
import com.tutorcraft.core.courses.ItemType;
import com.tutorcraft.core.progress.ProgressApi;
import com.tutorcraft.core.progress.domain.ProgressMath;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Реализация ProgressApi: только репозиторий выполнения и CoursesApi (правило против циклов). */
@Service
class ProgressApiImpl implements ProgressApi {

    private final CompletionRepository completions;
    private final CoursesApi courses;

    ProgressApiImpl(CompletionRepository completions, CoursesApi courses) {
        this.completions = completions;
        this.courses = courses;
    }

    /** Курсы без отслеживаемых элементов в ответ не попадают (процент не определён). */
    @Override
    public Map<UUID, Integer> completionPercents(UUID tenantId, UUID userId, Collection<UUID> courseIds) {
        if (courseIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<UUID>> tracked = TrackedItems.of(courses.itemsOfCourses(tenantId, courseIds, EnumSet.allOf(ItemType.class)))
                .stream().collect(Collectors.groupingBy(ItemRef::courseId, Collectors.mapping(ItemRef::id, Collectors.toList())));
        Map<UUID, Set<UUID>> completed = completions.completedItemsByCourse(tenantId, userId, courseIds);
        Map<UUID, Integer> percents = new HashMap<>();
        tracked.forEach((courseId, items) -> percents.put(courseId,
                ProgressMath.percent(items, completed.getOrDefault(courseId, Set.of()))));
        return percents;
    }

    @Override
    public Optional<Instant> courseCompletedAt(UUID tenantId, UUID courseId, UUID userId) {
        return completions.courseCompletedAt(tenantId, courseId, userId);
    }

    @Override
    public Optional<UUID> lastViewedItem(UUID tenantId, UUID courseId, UUID userId) {
        return completions.lastViewedItem(tenantId, courseId, userId);
    }
}
