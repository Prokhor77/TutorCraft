package com.tutorcraft.core.assessment.quiz.domain;

import com.tutorcraft.core.shared.domain.Validator;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Проверенное содержимое новой версии вопроса (QuestionInput). Блочные документы санитизирует прикладной слой.
 */
public record QuestionDraft(QuestionType type, String title, Map<String, Object> body, BigDecimal defaultScore,
                            UUID categoryId, List<String> tags, QuestionData data, Map<String, Object> generalFeedback) {

    static final int MAX_TITLE = 255;
    static final int MAX_TAGS = 20;
    static final int MAX_TAG = 64;
    static final BigDecimal MAX_DEFAULT_SCORE = BigDecimal.valueOf(1000);

    /** @throws com.tutorcraft.core.shared.domain.ValidationException при любом нарушении */
    public static QuestionDraft of(String typeKey, String title, Map<String, Object> body, BigDecimal defaultScore,
                                   UUID categoryId, List<String> tags, Map<String, Object> rawData,
                                   Map<String, Object> generalFeedback) {
        QuestionType type = QuestionType.fromKey(typeKey, "type");
        List<String> normalizedTags = normalizeTags(tags);
        new Validator()
            .notBlank(title, "title").maxLength(title, MAX_TITLE, "title")
            .check(body != null, "body", "required", "Question text is required")
            .check(defaultScore != null && defaultScore.signum() > 0 && defaultScore.compareTo(MAX_DEFAULT_SCORE) <= 0,
                    "defaultScore", "out_of_range", "Default score must be positive and at most " + MAX_DEFAULT_SCORE)
            .check(normalizedTags.size() <= MAX_TAGS, "tags", "too_many", "At most " + MAX_TAGS + " tags are allowed")
            .check(normalizedTags.stream().allMatch(tag -> tag.length() <= MAX_TAG), "tags", "too_long",
                    "Tag is longer than " + MAX_TAG)
            .check(rawData == null || rawData.get(QuestionData.TYPE) == null || type.key().equals(rawData.get(QuestionData.TYPE)),
                    "data.type", "type_mismatch", "data.type must match type")
            .throwIfInvalid();
        QuestionData data = QuestionDataParser.parse(type, rawData);
        return new QuestionDraft(type, title.strip(), body, defaultScore, categoryId, normalizedTags, data, generalFeedback);
    }

    private static List<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        return List.copyOf(new LinkedHashSet<>(tags.stream().filter(tag -> tag != null && !tag.isBlank())
                .map(String::strip).toList()));
    }
}
