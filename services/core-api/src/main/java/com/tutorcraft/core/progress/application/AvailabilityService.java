package com.tutorcraft.core.progress.application;

import com.tutorcraft.core.courses.ItemRef;
import com.tutorcraft.core.courses.ModuleRef;
import com.tutorcraft.core.enrollment.EnrollmentApi;
import com.tutorcraft.core.gradebook.GradebookApi;
import com.tutorcraft.core.gradebook.GradebookApi.GradeView;
import com.tutorcraft.core.progress.domain.AvailabilityTree;
import com.tutorcraft.core.progress.domain.AvailabilityTree.Node;
import com.tutorcraft.core.progress.domain.Condition;
import com.tutorcraft.core.progress.domain.ConditionGroup;
import com.tutorcraft.core.progress.domain.ConditionParser;
import com.tutorcraft.core.progress.domain.Evaluation;
import com.tutorcraft.core.progress.domain.LearnerFacts;
import com.tutorcraft.core.shared.domain.ValidationException;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Вычисление доступности модулей и элементов курса для студента: сбор фактов (выполнение, опубликованные оценки,
 * группы — только если на них ссылаются условия) и движок условий (FR-PROG-02/04).
 * Зависит только от репозитория и API, реализованных поверх репозиториев (правило против циклов).
 */
@Component
public class AvailabilityService {

    private static final Logger log = LoggerFactory.getLogger(AvailabilityService.class);

    private final CompletionRepository completions;
    private final GradebookApi gradebook;
    private final EnrollmentApi enrollment;
    private final Clock clock;

    public AvailabilityService(CompletionRepository completions, GradebookApi gradebook, EnrollmentApi enrollment, Clock clock) {
        this.completions = completions;
        this.gradebook = gradebook;
        this.enrollment = enrollment;
        this.clock = clock;
    }

    public Map<UUID, Evaluation> evaluate(UUID tenantId, UUID userId, UUID courseId, List<ModuleRef> modules,
                                          List<ItemRef> items, Set<UUID> completedItems) {
        List<Node> moduleNodes = modules.stream()
                .map(m -> new Node(m.id(), m.parentId(), parseSafely(m.conditions(), m.id()))).toList();
        List<Node> itemNodes = items.stream()
                .map(i -> new Node(i.id(), i.moduleId(), parseSafely(i.conditions(), i.id()))).toList();
        List<ConditionGroup> groups = Stream.concat(moduleNodes.stream(), itemNodes.stream())
                .map(Node::conditions).filter(Objects::nonNull).toList();
        LearnerFacts facts = facts(tenantId, userId, courseId, groups, completedItems);
        return AvailabilityTree.evaluate(moduleNodes, itemNodes, facts, clock.instant());
    }

    private LearnerFacts facts(UUID tenantId, UUID userId, UUID courseId, List<ConditionGroup> groups, Set<UUID> completed) {
        List<Condition> conditions = groups.stream().flatMap(group -> group.conditions().stream()).toList();
        Set<UUID> gradeItems = conditions.stream().filter(Condition.Grade.class::isInstance)
                .map(condition -> ((Condition.Grade) condition).itemId()).collect(Collectors.toSet());
        boolean needsGroups = conditions.stream().anyMatch(Condition.Group.class::isInstance);
        Map<UUID, Double> percents = new HashMap<>();
        if (!gradeItems.isEmpty()) {
            gradebook.gradesOf(tenantId, userId, gradeItems).forEach((itemId, grade) -> publishedPercent(grade)
                    .ifPresent(percent -> percents.put(itemId, percent)));
        }
        Set<UUID> groupIds = needsGroups ? enrollment.groupIds(tenantId, courseId, userId) : Set.of();
        return new LearnerFacts(completed, percents, groupIds);
    }

    /** Студент видит условие по оценке только по опубликованной оценке (не раскрывать скрытое). */
    private static Optional<Double> publishedPercent(GradeView grade) {
        return grade.published() ? grade.percent() : Optional.empty();
    }

    /** Условия проверяются при сохранении (ConditionSchema); повреждённые данные не блокируют курс. */
    private static ConditionGroup parseSafely(Map<String, Object> raw, UUID ownerId) {
        try {
            return ConditionParser.parse(raw);
        } catch (ValidationException e) {
            log.warn("Ignoring invalid access conditions of {}: {}", ownerId, e.violations().size());
            return null;
        }
    }
}
