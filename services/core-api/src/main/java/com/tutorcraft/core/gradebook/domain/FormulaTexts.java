package com.tutorcraft.core.gradebook.domain;

/**
 * Локализованные шаблоны (java.text.MessageFormat) для описания формулы итоговой оценки (FR-GRADE-03).
 * <ul>
 *   <li>{@code finalLabel} — «Итог»;</li>
 *   <li>{@code pointsFormula} — {0} итог, {1} сумма максимумов: «{0} = сумма баллов ÷ {1} × 100%»;</li>
 *   <li>{@code emptyFormula} — {0} итог: «{0}: нет оцениваемых элементов»;</li>
 *   <li>{@code weightsNot100} — {0} сумма весов: «Сумма весов {0}%, должно быть 100%»;</li>
 *   <li>{@code emptyCategory} — {0} название: «Категория «{0}» не содержит элементов»;</li>
 *   <li>{@code zeroMax} — {0} название: «У элемента «{0}» максимальный балл 0».</li>
 * </ul>
 * Числа передаются в шаблоны уже отформатированными строками (десятичная запятая для ru).
 */
public record FormulaTexts(String finalLabel, String pointsFormula, String emptyFormula, String weightsNot100,
                           String emptyCategory, String zeroMax) {
}
