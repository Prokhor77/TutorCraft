package com.tutorcraft.core.assessment.assignment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Валидация и нормализация настроек задания (DATA-04). Ошибки — поля {@code settings.<name>}. */
public final class AssignmentSettingsParser {

    public static final BigDecimal MAX_SCORE_LIMIT = BigDecimal.valueOf(10_000);
    public static final int MAX_FILES_LIMIT = 20;
    public static final int MAX_FILE_SIZE_MB_LIMIT = 100;
    public static final int MAX_ATTEMPTS_LIMIT = 100;
    private static final int MAX_EXTENSIONS = 50;
    private static final Pattern EXTENSION = Pattern.compile("[a-z0-9]{1,10}");
    private static final String DOT = ".";

    private AssignmentSettingsParser() {
    }

    /** @throws com.tutorcraft.core.shared.domain.ValidationException при нарушениях */
    public static AssignmentSettings parse(Map<String, Object> raw) {
        SettingsReader reader = new SettingsReader(raw);
        AssignmentSettings defaults = AssignmentSettings.defaults();
        AssignmentSettings settings = new AssignmentSettings(
                submissionType(reader, defaults.submissionType()),
                reader.decimal("maxScore", defaults.maxScore(), BigDecimal.ZERO, MAX_SCORE_LIMIT),
                reader.instant("dueAt"), reader.instant("openAt"), reader.instant("closeAt"),
                extensions(reader),
                reader.integer("maxFiles", defaults.maxFiles(), 1, MAX_FILES_LIMIT),
                reader.integer("maxFileSizeMb", defaults.maxFileSizeMb(), 1, MAX_FILE_SIZE_MB_LIMIT),
                reader.optionalInteger("maxAttempts", 1, MAX_ATTEMPTS_LIMIT),
                reader.bool("groupSubmission", defaults.groupSubmission()),
                reader.bool("requireSubmitButton", defaults.requireSubmitButton()),
                reader.uuid("gradeCategoryId"),
                reader.bool("autoPublishGrades", defaults.autoPublishGrades()));
        checkDateOrder(reader, settings);
        reader.throwIfInvalid();
        return settings;
    }

    private static SubmissionType submissionType(SettingsReader reader, SubmissionType fallback) {
        String key = reader.string("submissionType", fallback.key());
        return SubmissionType.find(key).orElseGet(() -> {
            reader.violation("submissionType", "invalid", "Must be one of file, text, both, none");
            return fallback;
        });
    }

    private static List<String> extensions(SettingsReader reader) {
        List<String> normalized = reader.strings("allowedExtensions").stream()
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .map(value -> value.startsWith(DOT) ? value.substring(1) : value)
                .distinct()
                .toList();
        if (normalized.size() > MAX_EXTENSIONS || !normalized.stream().allMatch(ext -> EXTENSION.matcher(ext).matches())) {
            reader.violation("allowedExtensions", "invalid", "Extensions must be 1-10 latin letters or digits");
            return List.of();
        }
        return normalized;
    }

    private static void checkDateOrder(SettingsReader reader, AssignmentSettings settings) {
        if (isAfter(settings.openAt(), settings.dueAt())) {
            reader.violation("dueAt", "before_open", "Due date must be after the open date");
        }
        if (isAfter(settings.dueAt(), settings.closeAt())) {
            reader.violation("closeAt", "before_due", "Close date must not be before the due date");
        }
        if (isAfter(settings.openAt(), settings.closeAt())) {
            reader.violation("closeAt", "before_open", "Close date must be after the open date");
        }
    }

    private static boolean isAfter(Instant first, Instant second) {
        return first != null && second != null && first.isAfter(second);
    }
}
