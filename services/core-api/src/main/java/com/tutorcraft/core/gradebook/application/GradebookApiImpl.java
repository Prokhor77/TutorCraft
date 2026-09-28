package com.tutorcraft.core.gradebook.application;

import com.tutorcraft.core.gradebook.GradebookApi;
import com.tutorcraft.core.gradebook.GradebookEvents.GradeChanged;
import com.tutorcraft.core.gradebook.application.GradebookStructureRepository.GradebookSettings;
import com.tutorcraft.core.gradebook.domain.FinalGradeCalculator;
import com.tutorcraft.core.gradebook.domain.Grade;
import com.tutorcraft.core.gradebook.domain.GradeColumn;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Реализация GradebookApi: только репозитории журнала и shared-ядро (правило против циклов бинов). */
@Component
class GradebookApiImpl implements GradebookApi {

    static final String REASON_SOURCE = "source";
    private static final Logger log = LoggerFactory.getLogger(GradebookApiImpl.class);

    private final GradebookStructureRepository structure;
    private final GradeRepository grades;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    GradebookApiImpl(GradebookStructureRepository structure, GradeRepository grades, ApplicationEventPublisher events,
                     Clock clock) {
        this.structure = structure;
        this.grades = grades;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional
    public UUID ensureGradeItem(UUID tenantId, UUID courseId, UUID sourceItemId, String name, BigDecimal maxScore,
                                UUID categoryId) {
        UUID validCategory = categoryId != null && structure.categories(tenantId, courseId).stream()
                .anyMatch(category -> category.id().equals(categoryId)) ? categoryId : null;
        return structure.upsertSourceColumn(tenantId, courseId, sourceItemId, name, maxScore, validCategory, clock.instant());
    }

    @Override
    @Transactional
    public void removeGradeItem(UUID tenantId, UUID sourceItemId) {
        structure.softDeleteBySource(tenantId, sourceItemId, clock.instant());
    }

    @Override
    @Transactional
    public void recordGrade(GradeUpdate update) {
        Optional<GradeColumn> column = structure.columnBySource(update.tenantId(), update.sourceItemId());
        if (column.isEmpty()) {
            log.warn("No grade column for source item {}, grade skipped", update.sourceItemId());
            return;
        }
        Instant now = clock.instant();
        Grade current = grades.lockOrCreate(update.tenantId(), column.get().id(), update.userId(), now);
        if (!current.acceptsSourceScore()) {
            return;
        }
        Grade next = current.withSourceScore(update.score(), update.graderId(), now, update.publish());
        grades.update(next, current.version(), now);
        if (current.scoreDiffers(next)) {
            grades.insertHistory(update.tenantId(), current.id(), current.finalScore(), next.finalScore(),
                    update.graderId(), REASON_SOURCE, now);
        }
        events.publishEvent(new GradeChanged(update.tenantId(), update.courseId(), update.sourceItemId(), update.userId(),
                next.finalScore(), column.get().maxScore(), next.published()));
    }

    @Override
    @Transactional
    public int publishAll(UUID tenantId, UUID sourceItemId, UUID actorId) {
        Optional<GradeColumn> column = structure.columnBySource(tenantId, sourceItemId);
        if (column.isEmpty()) {
            return 0;
        }
        List<Grade> published = grades.publishColumn(tenantId, column.get().id(), clock.instant());
        published.forEach(grade -> events.publishEvent(new GradeChanged(tenantId, column.get().courseId(), sourceItemId,
                grade.userId(), grade.finalScore(), column.get().maxScore(), true)));
        return published.size();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<GradeView> grade(UUID tenantId, UUID sourceItemId, UUID userId) {
        return Optional.ofNullable(gradesOf(tenantId, userId, List.of(sourceItemId)).get(sourceItemId));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, GradeView> gradesOf(UUID tenantId, UUID userId, Collection<UUID> sourceItemIds) {
        Map<UUID, GradeColumn> columns = structure.columnsBySource(tenantId, sourceItemIds).stream()
                .collect(Collectors.toMap(GradeColumn::id, Function.identity()));
        Map<UUID, GradeView> result = new HashMap<>();
        grades.ofUser(tenantId, userId, columns.keySet()).stream()
                .filter(grade -> grade.finalScore() != null)
                .forEach(grade -> {
                    GradeColumn column = columns.get(grade.columnId());
                    result.put(column.sourceItemId(), new GradeView(grade.finalScore(), column.maxScore(), grade.published()));
                });
        return result;
    }

    /** Итог по опубликованным оценкам — то, что видит студент (FR-GRADE-08). */
    @Override
    @Transactional(readOnly = true)
    public Optional<BigDecimal> finalPercent(UUID tenantId, UUID courseId, UUID userId) {
        GradebookSettings settings = structure.settings(tenantId, courseId).orElse(GradebookSettings.defaults(courseId));
        List<GradeColumn> columns = structure.columns(tenantId, courseId);
        Map<UUID, BigDecimal> scores = PublishedScores.of(grades.ofUser(tenantId, userId,
                columns.stream().map(GradeColumn::id).toList()));
        return FinalGradeCalculator.finalPercent(settings.aggregation(), structure.categories(tenantId, courseId), columns,
                scores);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PublishedGrade> recentlyPublished(UUID tenantId, UUID userId, int limit) {
        return grades.recentlyPublished(tenantId, userId, limit).stream()
                .map(grade -> new PublishedGrade(grade.courseId(), grade.sourceItemId(), grade.itemName(), grade.score(),
                        grade.maxScore(), grade.publishedAt()))
                .toList();
    }
}
