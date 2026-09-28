package com.tutorcraft.core.gradebook.domain;

import java.math.BigDecimal;

/** Уровень шкалы: словесная оценка и нижняя граница в процентах (FR-GRADE-05). */
public record ScaleLevel(String name, BigDecimal minPercent) {
}
