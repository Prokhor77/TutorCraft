package com.tutorcraft.core.progress.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.tutorcraft.core.progress.domain.Condition;
import com.tutorcraft.core.progress.domain.ConditionEvaluator;
import com.tutorcraft.core.progress.domain.ConditionGroup;
import com.tutorcraft.core.progress.domain.ConditionGroup.Operator;
import com.tutorcraft.core.progress.domain.Evaluation;
import com.tutorcraft.core.progress.domain.LearnerFacts;
import com.tutorcraft.core.shared.i18n.Messages;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

/** AC-6: точный текст пояснения недоступности. */
class ReasonTextsTest {

    private static final Locale RU = Locale.forLanguageTag("ru");
    private static final Instant NOW = Instant.parse("2026-09-27T10:00:00Z");
    private static final UUID TASK = new UUID(0, 1);
    private static final UUID OTHER = new UUID(0, 2);

    private final ReasonTexts texts = new ReasonTexts(new Messages(messageSource()));

    private static ResourceBundleMessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames("i18n/progress");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return source;
    }

    private static Evaluation locked(Operator operator, LearnerFacts facts, Condition... conditions) {
        return ConditionEvaluator.evaluate(new ConditionGroup(operator, true, List.of(conditions)), facts, NOW);
    }

    @Test
    void gradeBelowMinimumMatchesAcceptanceCriterion() {
        Evaluation evaluation = locked(Operator.ALL, new LearnerFacts(Set.of(), Map.of(TASK, 50.0), Set.of()),
                new Condition.Grade(TASK, 60.0, null));
        assertThat(texts.format(evaluation, Map.of(TASK, "Задание 1"), ZoneOffset.UTC, RU))
                .containsExactly("Откроется, когда: оценка за „Задание 1“ не ниже 60% (сейчас 50%)");
    }

    @Test
    void anyConditionsAreJoinedWithOrAndUnknownTitlesAreNamed() {
        Evaluation evaluation = locked(Operator.ANY, LearnerFacts.none(), new Condition.Completion(TASK, true),
                new Condition.Grade(OTHER, 62.5, null));
        assertThat(texts.format(evaluation, Map.of(TASK, "Лекция"), ZoneOffset.UTC, RU)).containsExactly(
                "Откроется, когда: выполнен элемент „Лекция“ или оценка за „удалённый элемент“ не ниже 62,5% (оценки пока нет)");
    }

    @Test
    void expiredWindowIsReportedSeparately() {
        Evaluation evaluation = locked(Operator.ALL, LearnerFacts.none(), new Condition.DateWindow(null, NOW),
                new Condition.Group(OTHER));
        List<String> reasons = texts.format(evaluation, Map.of(), ZoneOffset.UTC, RU);
        assertThat(reasons).hasSize(2);
        assertThat(reasons.get(0)).isEqualTo("Откроется, когда: вы состоите в нужной группе");
        assertThat(reasons.get(1)).startsWith("Доступ закрыт ");
    }

    @Test
    void openItemHasNoReasons() {
        assertThat(texts.format(Evaluation.OPEN, Map.of(), ZoneOffset.UTC, RU)).isEmpty();
    }
}
