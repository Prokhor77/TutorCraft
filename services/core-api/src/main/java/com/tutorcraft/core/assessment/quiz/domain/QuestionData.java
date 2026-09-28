package com.tutorcraft.core.assessment.quiz.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Данные вопроса по типу (контракт §10, QuestionData). Содержат ключи ответов — никогда не отдаются студенту
 * (NFR-SEC-08); для студента используется {@link PublicQuestionParts}.
 */
public sealed interface QuestionData {

    String TYPE = "type";

    QuestionType type();

    /** Представление для хранения в MongoDB и ответа преподавателю. */
    Map<String, Object> toMap();

    record ChoiceOption(String id, String text, boolean correct, String feedback) {

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", id);
            map.put("text", text);
            map.put("correct", correct);
            map.put("feedback", feedback);
            return map;
        }
    }

    record SingleChoice(List<ChoiceOption> options, boolean shuffle) implements QuestionData {

        @Override
        public QuestionType type() {
            return QuestionType.SINGLE_CHOICE;
        }

        @Override
        public Map<String, Object> toMap() {
            return base(type(), "options", options.stream().map(ChoiceOption::toMap).toList(), "shuffle", shuffle);
        }
    }

    record MultipleChoice(List<ChoiceOption> options, boolean shuffle, MultipleChoiceScoring scoring) implements QuestionData {

        @Override
        public QuestionType type() {
            return QuestionType.MULTIPLE_CHOICE;
        }

        @Override
        public Map<String, Object> toMap() {
            Map<String, Object> map = base(type(), "options", options.stream().map(ChoiceOption::toMap).toList(), "shuffle", shuffle);
            map.put("scoring", scoring.key());
            return map;
        }

        public Set<String> correctIds() {
            return options.stream().filter(ChoiceOption::correct).map(ChoiceOption::id).collect(Collectors.toSet());
        }
    }

    record TrueFalse(boolean correct) implements QuestionData {

        @Override
        public QuestionType type() {
            return QuestionType.TRUE_FALSE;
        }

        @Override
        public Map<String, Object> toMap() {
            return base(type(), "correct", correct, null, null);
        }
    }

    record PatternAnswer(String pattern, double scorePercent) {
    }

    record ShortAnswer(List<PatternAnswer> answers, boolean caseSensitive) implements QuestionData {

        @Override
        public QuestionType type() {
            return QuestionType.SHORT_ANSWER;
        }

        @Override
        public Map<String, Object> toMap() {
            List<Map<String, Object>> list = answers.stream()
                    .map(a -> Map.<String, Object>of("pattern", a.pattern(), "scorePercent", a.scorePercent())).toList();
            return base(type(), "answers", list, "caseSensitive", caseSensitive);
        }
    }

    record NumericAnswer(double value, double tolerance, double scorePercent) {
    }

    record Numerical(List<NumericAnswer> answers) implements QuestionData {

        @Override
        public QuestionType type() {
            return QuestionType.NUMERICAL;
        }

        @Override
        public Map<String, Object> toMap() {
            List<Map<String, Object>> list = answers.stream().map(a -> Map.<String, Object>of(
                    "value", a.value(), "tolerance", a.tolerance(), "scorePercent", a.scorePercent())).toList();
            return base(type(), "answers", list, null, null);
        }
    }

    record Essay(EssayFormat responseFormat, Integer minWords, Integer maxWords) implements QuestionData {

        @Override
        public QuestionType type() {
            return QuestionType.ESSAY;
        }

        @Override
        public Map<String, Object> toMap() {
            Map<String, Object> map = base(type(), "responseFormat", responseFormat.key(), "minWords", minWords);
            map.put("maxWords", maxWords);
            return map;
        }
    }

    record MatchPair(String id, String prompt, String answer) {
    }

    record Matching(List<MatchPair> pairs, boolean shuffle) implements QuestionData {

        @Override
        public QuestionType type() {
            return QuestionType.MATCHING;
        }

        @Override
        public Map<String, Object> toMap() {
            List<Map<String, Object>> list = pairs.stream()
                    .map(p -> Map.<String, Object>of("id", p.id(), "prompt", p.prompt(), "answer", p.answer())).toList();
            return base(type(), "pairs", list, "shuffle", shuffle);
        }
    }

    record OrderItem(String id, String text) {
    }

    /** Правильный порядок = порядок массива {@code items}. */
    record Ordering(List<OrderItem> items) implements QuestionData {

        @Override
        public QuestionType type() {
            return QuestionType.ORDERING;
        }

        @Override
        public Map<String, Object> toMap() {
            List<Map<String, Object>> list = items.stream().map(i -> Map.<String, Object>of("id", i.id(), "text", i.text())).toList();
            return base(type(), "items", list, null, null);
        }
    }

    private static Map<String, Object> base(QuestionType type, String key1, Object value1, String key2, Object value2) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(TYPE, type.key());
        map.put(key1, value1);
        if (key2 != null) {
            map.put(key2, value2);
        }
        return map;
    }
}
