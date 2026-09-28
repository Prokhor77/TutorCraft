package com.tutorcraft.core.assessment.quiz.domain;

import java.math.BigDecimal;
import java.util.UUID;

/** Текущая версия вопроса банка, пригодная для включения в попытку. */
public record QuestionCandidate(UUID questionId, UUID versionId, BigDecimal defaultScore, QuestionData data) {
}
