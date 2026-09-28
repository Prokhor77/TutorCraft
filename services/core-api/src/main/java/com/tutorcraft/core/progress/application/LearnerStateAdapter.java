package com.tutorcraft.core.progress.application;

import com.tutorcraft.core.courses.Availability;
import com.tutorcraft.core.courses.CourseRef;
import com.tutorcraft.core.courses.CoursesApi;
import com.tutorcraft.core.courses.ItemRef;
import com.tutorcraft.core.courses.ModuleRef;
import com.tutorcraft.core.courses.spi.LearnerStateProvider;
import com.tutorcraft.core.identity.UsersApi;
import com.tutorcraft.core.progress.domain.CompletionRule;
import com.tutorcraft.core.progress.domain.Evaluation;
import com.tutorcraft.core.progress.domain.Reason;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * Доступность и выполнение для оглавления курса (LearnerStateProvider). Причины — на языке запроса,
 * даты — в часовом поясе студента (DATA-06).
 */
@Component
class LearnerStateAdapter implements LearnerStateProvider {

    static final String COMPLETE = "complete";
    static final String INCOMPLETE = "incomplete";

    private final AvailabilityService availability;
    private final CompletionRepository completions;
    private final ReasonTexts reasonTexts;
    private final UsersApi users;
    private final CoursesApi courses;

    LearnerStateAdapter(AvailabilityService availability, CompletionRepository completions, ReasonTexts reasonTexts,
                        UsersApi users, CoursesApi courses) {
        this.availability = availability;
        this.completions = completions;
        this.reasonTexts = reasonTexts;
        this.users = users;
        this.courses = courses;
    }

    @Override
    public LearnerState stateFor(UUID tenantId, UUID userId, CourseRef course, List<ModuleRef> modules, List<ItemRef> items) {
        Set<UUID> completed = completions.completedItems(tenantId, course.id(), userId);
        Map<UUID, Evaluation> evaluations = availability.evaluate(tenantId, userId, course.id(), modules, items, completed);
        Map<UUID, String> titles = titles(tenantId, items, evaluations);
        ZoneId zone = zoneOf(tenantId, userId);
        Locale locale = LocaleContextHolder.getLocale();
        Map<UUID, Availability> result = new HashMap<>();
        evaluations.forEach((id, evaluation) -> result.put(id, new Availability(evaluation.available(),
                evaluation.showWhenLocked() ? Availability.SHOW_LOCKED : Availability.HIDE,
                reasonTexts.format(evaluation, titles, zone, locale))));
        return new LearnerState(result, completionMap(items, completed));
    }

    /** Названия элементов из причин; скрытые от студента элементы (нет в списке) дочитываются из courses. */
    private Map<UUID, String> titles(UUID tenantId, List<ItemRef> items, Map<UUID, Evaluation> evaluations) {
        Map<UUID, String> titles = new HashMap<>(items.stream()
                .collect(Collectors.toMap(ItemRef::id, ItemRef::title, (first, second) -> first)));
        Set<UUID> missing = evaluations.values().stream().flatMap(evaluation -> evaluation.reasons().stream())
                .map(Reason::itemId).filter(id -> id != null && !titles.containsKey(id)).collect(Collectors.toSet());
        if (!missing.isEmpty()) {
            courses.findItems(tenantId, missing).forEach((id, item) -> titles.put(id, item.title()));
        }
        return titles;
    }

    static Map<UUID, String> completionMap(List<ItemRef> items, Set<UUID> completed) {
        Map<UUID, String> map = new HashMap<>();
        items.stream().filter(item -> CompletionRule.of(item.completionMode(), item.completionTriggers()).tracked())
                .forEach(item -> map.put(item.id(), completed.contains(item.id()) ? COMPLETE : INCOMPLETE));
        return map;
    }

    private ZoneId zoneOf(UUID tenantId, UUID userId) {
        String timezone = users.find(tenantId, userId).map(UsersApi.UserRef::timezone).orElse(null);
        if (timezone == null || timezone.isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException e) {
            return ZoneOffset.UTC;
        }
    }
}
