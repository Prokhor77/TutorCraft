package com.tutorcraft.core.assessment.quiz.domain;

import com.tutorcraft.core.assessment.quiz.domain.QuestionData.ChoiceOption;
import com.tutorcraft.core.assessment.quiz.domain.QuestionData.MatchPair;
import com.tutorcraft.core.assessment.quiz.domain.QuestionData.NumericAnswer;
import com.tutorcraft.core.assessment.quiz.domain.QuestionData.OrderItem;
import com.tutorcraft.core.assessment.quiz.domain.QuestionData.PatternAnswer;
import com.tutorcraft.core.shared.domain.Validator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Разбор и валидация {@code QuestionData} по типу вопроса (FR-QBANK-03). Все нарушения собираются
 * с путями полей вида {@code data.options[1].text}.
 */
public final class QuestionDataParser {

    static final int MIN_CHOICES = 2;
    static final int MAX_CHOICES = 50;
    static final int MAX_TEXT = 2000;
    static final int MAX_ID = 64;
    static final double FULL_PERCENT = 100.0;
    private static final String FIELD = "data";

    private QuestionDataParser() {
    }

    /** @throws com.tutorcraft.core.shared.domain.ValidationException при любом нарушении */
    public static QuestionData parse(QuestionType type, Map<String, Object> data) {
        Validator validator = new Validator();
        validator.check(data != null, FIELD, "required", "Question data is required");
        validator.throwIfInvalid();
        QuestionData parsed = switch (type) {
            case SINGLE_CHOICE -> singleChoice(data, validator);
            case MULTIPLE_CHOICE -> multipleChoice(data, validator);
            case TRUE_FALSE -> trueFalse(data, validator);
            case SHORT_ANSWER -> shortAnswer(data, validator);
            case NUMERICAL -> numerical(data, validator);
            case ESSAY -> essay(data, validator);
            case MATCHING -> matching(data, validator);
            case ORDERING -> ordering(data, validator);
        };
        validator.throwIfInvalid();
        return parsed;
    }

    private static QuestionData singleChoice(Map<String, Object> data, Validator validator) {
        List<ChoiceOption> options = options(data, validator);
        long correct = options.stream().filter(ChoiceOption::correct).count();
        validator.check(correct == 1, FIELD + ".options", "exactly_one_correct", "Exactly one option must be correct");
        return new QuestionData.SingleChoice(options, MapValues.bool(data, "shuffle", FIELD + ".shuffle", true));
    }

    private static QuestionData multipleChoice(Map<String, Object> data, Validator validator) {
        List<ChoiceOption> options = options(data, validator);
        validator.check(options.stream().anyMatch(ChoiceOption::correct), FIELD + ".options", "correct_required",
                "At least one option must be correct");
        String scoringKey = MapValues.string(data, "scoring", FIELD + ".scoring");
        MultipleChoiceScoring scoring = MultipleChoiceScoring.find(scoringKey == null ? MultipleChoiceScoring.PARTIAL.key() : scoringKey)
                .orElse(null);
        validator.check(scoring != null, FIELD + ".scoring", "invalid", "Unknown scoring policy");
        boolean shuffle = MapValues.bool(data, "shuffle", FIELD + ".shuffle", true);
        return new QuestionData.MultipleChoice(options, shuffle, scoring == null ? MultipleChoiceScoring.PARTIAL : scoring);
    }

    private static QuestionData trueFalse(Map<String, Object> data, Validator validator) {
        Boolean correct = MapValues.bool(data, "correct", FIELD + ".correct");
        validator.check(correct != null, FIELD + ".correct", "required", "Correct value is required");
        return new QuestionData.TrueFalse(Boolean.TRUE.equals(correct));
    }

    private static QuestionData shortAnswer(Map<String, Object> data, Validator validator) {
        List<?> raw = sizedList(data, "answers", 1, validator);
        List<PatternAnswer> answers = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            String field = FIELD + ".answers[" + i + "]";
            Map<String, Object> answer = MapValues.asObject(raw.get(i), field);
            String pattern = MapValues.string(answer, "pattern", field + ".pattern");
            validator.notBlank(pattern, field + ".pattern").maxLength(pattern, MAX_TEXT, field + ".pattern");
            answers.add(new PatternAnswer(pattern == null ? "" : pattern.strip(), percent(answer, field, validator)));
        }
        requireFullScore(answers.stream().mapToDouble(PatternAnswer::scorePercent).max().orElse(0), validator);
        return new QuestionData.ShortAnswer(answers, MapValues.bool(data, "caseSensitive", FIELD + ".caseSensitive", false));
    }

    private static QuestionData numerical(Map<String, Object> data, Validator validator) {
        List<?> raw = sizedList(data, "answers", 1, validator);
        List<NumericAnswer> answers = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            String field = FIELD + ".answers[" + i + "]";
            Map<String, Object> answer = MapValues.asObject(raw.get(i), field);
            Double value = MapValues.number(answer, "value", field + ".value");
            Double tolerance = MapValues.number(answer, "tolerance", field + ".tolerance");
            validator.check(value != null, field + ".value", "required", "Value is required")
                .check(tolerance == null || tolerance >= 0, field + ".tolerance", "negative", "Tolerance must not be negative");
            answers.add(new NumericAnswer(value == null ? 0 : value, tolerance == null ? 0 : tolerance, percent(answer, field, validator)));
        }
        requireFullScore(answers.stream().mapToDouble(NumericAnswer::scorePercent).max().orElse(0), validator);
        return new QuestionData.Numerical(answers);
    }

    private static QuestionData essay(Map<String, Object> data, Validator validator) {
        String formatKey = MapValues.string(data, "responseFormat", FIELD + ".responseFormat");
        EssayFormat format = EssayFormat.find(formatKey == null ? EssayFormat.TEXT.key() : formatKey).orElse(null);
        Integer minWords = MapValues.integer(data, "minWords", FIELD + ".minWords");
        Integer maxWords = MapValues.integer(data, "maxWords", FIELD + ".maxWords");
        validator.check(format != null, FIELD + ".responseFormat", "invalid", "Unknown response format")
            .check(minWords == null || minWords >= 0, FIELD + ".minWords", "negative", "Must not be negative")
            .check(maxWords == null || maxWords > 0, FIELD + ".maxWords", "invalid", "Must be positive")
            .check(minWords == null || maxWords == null || minWords <= maxWords, FIELD + ".maxWords", "less_than_min",
                    "maxWords must not be less than minWords");
        return new QuestionData.Essay(format == null ? EssayFormat.TEXT : format, minWords, maxWords);
    }

    private static QuestionData matching(Map<String, Object> data, Validator validator) {
        List<?> raw = sizedList(data, "pairs", MIN_CHOICES, validator);
        List<MatchPair> pairs = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < raw.size(); i++) {
            String field = FIELD + ".pairs[" + i + "]";
            Map<String, Object> pair = MapValues.asObject(raw.get(i), field);
            String id = id(pair, field, ids, validator);
            String prompt = text(pair, "prompt", field, validator);
            String answer = text(pair, "answer", field, validator);
            pairs.add(new MatchPair(id, prompt, answer));
        }
        return new QuestionData.Matching(pairs, MapValues.bool(data, "shuffle", FIELD + ".shuffle", true));
    }

    private static QuestionData ordering(Map<String, Object> data, Validator validator) {
        List<?> raw = sizedList(data, "items", MIN_CHOICES, validator);
        List<OrderItem> items = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < raw.size(); i++) {
            String field = FIELD + ".items[" + i + "]";
            Map<String, Object> item = MapValues.asObject(raw.get(i), field);
            items.add(new OrderItem(id(item, field, ids, validator), text(item, "text", field, validator)));
        }
        return new QuestionData.Ordering(items);
    }

    private static List<ChoiceOption> options(Map<String, Object> data, Validator validator) {
        List<?> raw = sizedList(data, "options", MIN_CHOICES, validator);
        List<ChoiceOption> options = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < raw.size(); i++) {
            String field = FIELD + ".options[" + i + "]";
            Map<String, Object> option = MapValues.asObject(raw.get(i), field);
            String id = id(option, field, ids, validator);
            String text = text(option, "text", field, validator);
            String feedback = MapValues.string(option, "feedback", field + ".feedback");
            validator.maxLength(feedback, MAX_TEXT, field + ".feedback");
            options.add(new ChoiceOption(id, text, MapValues.bool(option, "correct", field + ".correct", false), feedback));
        }
        return options;
    }

    private static List<?> sizedList(Map<String, Object> data, String key, int min, Validator validator) {
        List<?> list = MapValues.list(data, key, FIELD + "." + key);
        validator.check(list.size() >= min, FIELD + "." + key, "too_few", "At least " + min + " entries are required")
            .check(list.size() <= MAX_CHOICES, FIELD + "." + key, "too_many", "At most " + MAX_CHOICES + " entries are allowed");
        return list.size() <= MAX_CHOICES ? list : List.of();
    }

    private static String id(Map<String, Object> entry, String field, Set<String> seen, Validator validator) {
        String id = MapValues.string(entry, "id", field + ".id");
        validator.notBlank(id, field + ".id").maxLength(id, MAX_ID, field + ".id")
            .check(id == null || seen.add(id), field + ".id", "duplicate", "Identifiers must be unique");
        return id;
    }

    private static String text(Map<String, Object> entry, String key, String field, Validator validator) {
        String text = MapValues.string(entry, key, field + "." + key);
        validator.notBlank(text, field + "." + key).maxLength(text, MAX_TEXT, field + "." + key);
        return text;
    }

    private static double percent(Map<String, Object> answer, String field, Validator validator) {
        Double percent = MapValues.number(answer, "scorePercent", field + ".scorePercent");
        double value = percent == null ? FULL_PERCENT : percent;
        validator.check(value >= 0 && value <= FULL_PERCENT, field + ".scorePercent", "out_of_range", "Must be between 0 and 100");
        return value;
    }

    private static void requireFullScore(double best, Validator validator) {
        validator.check(best == FULL_PERCENT, FIELD + ".answers", "full_score_required", "At least one answer must give 100%");
    }
}
