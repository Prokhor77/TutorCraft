package com.tutorcraft.core.gradebook.domain;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Формула итоговой оценки человеческим языком и предупреждения о проблемах настройки (FR-GRADE-03, AC-7):
 * «Итог = 0,4 × Задания + 0,5 × Тесты», «Сумма весов 90%, должно быть 100%».
 */
public final class FormulaDescriber {

    public static final String WEIGHTS_NOT_100 = "weights_not_100";
    public static final String EMPTY_CATEGORY = "empty_category";
    public static final String ZERO_MAX = "zero_max";

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final String NUMBER_PATTERN = "0.####";
    private static final String EQUALS = " = ";
    private static final String TIMES = " × ";
    private static final String PLUS = " + ";

    private FormulaDescriber() {
    }

    public record Warning(String code, String message) {
    }

    public record Preview(String formula, List<Warning> warnings) {
    }

    public static Preview describe(Aggregation aggregation, List<GradeCategory> categories, List<GradeColumn> columns,
                                   FormulaTexts texts, Locale locale) {
        List<GradeCategory> ordered = categories.stream().sorted(Comparator.comparingInt(GradeCategory::position)).toList();
        boolean weighted = aggregation == Aggregation.WEIGHTED_MEAN && !ordered.isEmpty();
        String formula = weighted ? weightedFormula(ordered, texts, locale) : pointsFormula(columns, texts, locale);
        return new Preview(formula, warnings(weighted, ordered, columns, texts, locale));
    }

    private static String weightedFormula(List<GradeCategory> categories, FormulaTexts texts, Locale locale) {
        String terms = categories.stream()
                .map(category -> number(category.weight().divide(HUNDRED), locale) + TIMES + category.name())
                .collect(Collectors.joining(PLUS));
        return texts.finalLabel() + EQUALS + terms;
    }

    private static String pointsFormula(List<GradeColumn> columns, FormulaTexts texts, Locale locale) {
        BigDecimal total = columns.stream().map(GradeColumn::maxScore).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (columns.isEmpty() || total.signum() == 0) {
            return format(texts.emptyFormula(), locale, texts.finalLabel());
        }
        return format(texts.pointsFormula(), locale, texts.finalLabel(), number(total, locale));
    }

    private static List<Warning> warnings(boolean weighted, List<GradeCategory> categories, List<GradeColumn> columns,
                                          FormulaTexts texts, Locale locale) {
        List<Warning> warnings = new ArrayList<>();
        if (weighted) {
            BigDecimal sum = categories.stream().map(GradeCategory::weight).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (sum.compareTo(HUNDRED) != 0) {
                warnings.add(new Warning(WEIGHTS_NOT_100, format(texts.weightsNot100(), locale, number(sum, locale))));
            }
            categories.stream()
                    .filter(category -> columns.stream().noneMatch(column -> Objects.equals(column.categoryId(), category.id())))
                    .forEach(category -> warnings.add(new Warning(EMPTY_CATEGORY,
                            format(texts.emptyCategory(), locale, category.name()))));
        }
        columns.stream()
                .filter(column -> column.maxScore().signum() == 0)
                .forEach(column -> warnings.add(new Warning(ZERO_MAX, format(texts.zeroMax(), locale, column.name()))));
        return warnings;
    }

    /** Число без лишних нулей с десятичным разделителем локали: 0.4 → «0,4» (ru), «0.4» (en). */
    static String number(BigDecimal value, Locale locale) {
        DecimalFormat format = new DecimalFormat(NUMBER_PATTERN, DecimalFormatSymbols.getInstance(locale));
        return format.format(value);
    }

    private static String format(String template, Locale locale, Object... args) {
        return new MessageFormat(template, locale).format(args);
    }
}
