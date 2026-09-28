package com.tutorcraft.core.assessment.assignment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Настройки задания (контракт AssignmentSettings, FR-ASSIGN-01/02). Умолчания — AC-2:
 * сдача файлом, 100 баллов, без срока, до 5 файлов по 50 МБ, нужна кнопка «Отправить», оценки публикуются сразу.
 */
public record AssignmentSettings(SubmissionType submissionType, BigDecimal maxScore, Instant dueAt, Instant openAt,
                                 Instant closeAt, List<String> allowedExtensions, int maxFiles, int maxFileSizeMb,
                                 Integer maxAttempts, boolean groupSubmission, boolean requireSubmitButton,
                                 UUID gradeCategoryId, boolean autoPublishGrades) {

    public static final String KIND = "assignment";
    public static final BigDecimal DEFAULT_MAX_SCORE = BigDecimal.valueOf(100);
    public static final int DEFAULT_MAX_FILES = 5;
    public static final int DEFAULT_MAX_FILE_SIZE_MB = 50;
    private static final long BYTES_PER_MB = 1024L * 1024L;

    public AssignmentSettings {
        allowedExtensions = allowedExtensions == null ? List.of() : List.copyOf(allowedExtensions);
    }

    public static AssignmentSettings defaults() {
        return new AssignmentSettings(SubmissionType.FILE, DEFAULT_MAX_SCORE, null, null, null, List.of(),
                DEFAULT_MAX_FILES, DEFAULT_MAX_FILE_SIZE_MB, null, false, true, null, true);
    }

    public long maxFileSizeBytes() {
        return maxFileSizeMb * BYTES_PER_MB;
    }

    /** Нормализованное представление для хранения в Item.settings (даты — ISO-8601 UTC). */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("kind", KIND);
        map.put("submissionType", submissionType.key());
        map.put("maxScore", plainNumber(maxScore));
        map.put("dueAt", iso(dueAt));
        map.put("openAt", iso(openAt));
        map.put("closeAt", iso(closeAt));
        map.put("allowedExtensions", allowedExtensions);
        map.put("maxFiles", maxFiles);
        map.put("maxFileSizeMb", maxFileSizeMb);
        map.put("maxAttempts", maxAttempts);
        map.put("groupSubmission", groupSubmission);
        map.put("requireSubmitButton", requireSubmitButton);
        map.put("gradeCategoryId", gradeCategoryId == null ? null : gradeCategoryId.toString());
        map.put("autoPublishGrades", autoPublishGrades);
        return map;
    }

    private static String iso(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private static Number plainNumber(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        if (stripped.scale() <= 0) {
            return Long.valueOf(stripped.longValueExact());
        }
        return Double.valueOf(stripped.doubleValue());
    }
}
