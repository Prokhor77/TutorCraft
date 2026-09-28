package com.tutorcraft.core.gradebook.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.tutorcraft.core.gradebook.domain.FormulaDescriber.Preview;
import com.tutorcraft.core.gradebook.domain.FormulaDescriber.Warning;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** FR-GRADE-03, AC-7: тексты совпадают с i18n/gradebook_ru.properties и gradebook_en.properties. */
class FormulaDescriberTest {

    private static final Locale RU = Locale.forLanguageTag("ru");
    private static final FormulaTexts RU_TEXTS = new FormulaTexts("Итог", "{0} = сумма баллов ÷ {1} × 100%",
            "{0}: нет оцениваемых элементов", "Сумма весов {0}%, должно быть 100%", "В категории «{0}» нет элементов",
            "У элемента «{0}» максимальный балл 0");
    private static final FormulaTexts EN_TEXTS = new FormulaTexts("Final", "{0} = total points ÷ {1} × 100%",
            "{0}: no graded items yet", "Weights add up to {0}%, should be 100%", "Category \"{0}\" has no items",
            "Item \"{0}\" has a maximum score of 0");

    @Test
    void acceptanceCriterion7() {
        GradeCategory tasks = category("Задания", "40", 0);
        GradeCategory tests = category("Тесты", "50", 1);
        List<GradeColumn> columns = List.of(column("Эссе", "100", tasks), column("Тест 1", "20", tests));

        Preview preview = FormulaDescriber.describe(Aggregation.WEIGHTED_MEAN, List.of(tasks, tests), columns, RU_TEXTS, RU);

        assertThat(preview.formula()).isEqualTo("Итог = 0,4 × Задания + 0,5 × Тесты");
        assertThat(preview.warnings()).containsExactly(
                new Warning(FormulaDescriber.WEIGHTS_NOT_100, "Сумма весов 90%, должно быть 100%"));
    }

    @Test
    void englishUsesDecimalPointAndCategoryOrderByPosition() {
        GradeCategory tests = category("Quizzes", "62.5", 1);
        GradeCategory tasks = category("Tasks", "37.5", 0);
        List<GradeColumn> columns = List.of(column("Essay", "10", tasks), column("Quiz", "10", tests));

        Preview preview = FormulaDescriber.describe(Aggregation.WEIGHTED_MEAN, List.of(tests, tasks), columns, EN_TEXTS,
                Locale.ENGLISH);

        assertThat(preview.formula()).isEqualTo("Final = 0.375 × Tasks + 0.625 × Quizzes");
        assertThat(preview.warnings()).isEmpty();
    }

    @Test
    void emptyCategoryAndZeroMaxAreReported() {
        GradeCategory tasks = category("Задания", "60", 0);
        GradeCategory tests = category("Тесты", "40", 1);
        List<GradeColumn> columns = List.of(column("Проект", "0", tasks));

        Preview preview = FormulaDescriber.describe(Aggregation.WEIGHTED_MEAN, List.of(tasks, tests), columns, RU_TEXTS, RU);

        assertThat(preview.warnings()).containsExactly(
                new Warning(FormulaDescriber.EMPTY_CATEGORY, "В категории «Тесты» нет элементов"),
                new Warning(FormulaDescriber.ZERO_MAX, "У элемента «Проект» максимальный балл 0"));
    }

    @Test
    void sumAggregationDescribesPointsAndIgnoresCategoryWarnings() {
        GradeCategory tasks = category("Задания", "10", 0);
        List<GradeColumn> columns = List.of(column("Эссе", "100", tasks), column("Лаба", "150.5", null));

        Preview preview = FormulaDescriber.describe(Aggregation.SUM, List.of(tasks), columns, RU_TEXTS, RU);

        assertThat(preview.formula()).isEqualTo("Итог = сумма баллов ÷ 250,5 × 100%");
        assertThat(preview.warnings()).isEmpty();
    }

    @Test
    void weightedMeanWithoutCategoriesDescribesPoints() {
        Preview preview = FormulaDescriber.describe(Aggregation.WEIGHTED_MEAN, List.of(),
                List.of(column("Эссе", "40", null)), RU_TEXTS, RU);

        assertThat(preview.formula()).isEqualTo("Итог = сумма баллов ÷ 40 × 100%");
    }

    @Test
    void noItemsGiveEmptyFormula() {
        assertThat(FormulaDescriber.describe(Aggregation.SUM, List.of(), List.of(), RU_TEXTS, RU).formula())
                .isEqualTo("Итог: нет оцениваемых элементов");
        assertThat(FormulaDescriber.describe(Aggregation.SUM, List.of(), List.of(column("Ноль", "0", null)), RU_TEXTS, RU)
                .formula()).isEqualTo("Итог: нет оцениваемых элементов");
    }

    @Test
    void numbersAreFormattedPerLocale() {
        assertThat(FormulaDescriber.number(new BigDecimal("0.40000"), RU)).isEqualTo("0,4");
        assertThat(FormulaDescriber.number(new BigDecimal("0.40000"), Locale.ENGLISH)).isEqualTo("0.4");
        assertThat(FormulaDescriber.number(new BigDecimal("100.000"), RU)).isEqualTo("100");
    }

    private static GradeCategory category(String name, String weight, int position) {
        return new GradeCategory(UUID.randomUUID(), name, new BigDecimal(weight), position);
    }

    private static GradeColumn column(String name, String maxScore, GradeCategory category) {
        return new GradeColumn(UUID.randomUUID(), UUID.randomUUID(), null, name, new BigDecimal(maxScore),
                category == null ? null : category.id(), 0);
    }
}
