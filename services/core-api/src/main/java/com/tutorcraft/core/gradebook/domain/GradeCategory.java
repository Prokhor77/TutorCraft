package com.tutorcraft.core.gradebook.domain;

import java.math.BigDecimal;
import java.util.UUID;

/** Категория оценивания с весом в процентах (0..100), например «Задания 40». */
public record GradeCategory(UUID id, String name, BigDecimal weight, int position) {
}
