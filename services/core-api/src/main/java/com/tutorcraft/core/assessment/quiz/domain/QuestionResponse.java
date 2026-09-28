package com.tutorcraft.core.assessment.quiz.domain;

import com.tutorcraft.core.shared.domain.ValidationException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ответ студента (контракт §10, QuestionResponse). Форма определяется единственным ключом объекта.
 * Хранится в {@code attempt_answers.response} как JSON ({@link #toMap()}).
 */
public sealed interface QuestionResponse {

    int MAX_ITEMS = QuestionDataParser.MAX_CHOICES;
    int MAX_TEXT = QuestionDataParser.MAX_TEXT;

    Map<String, Object> toMap();

    record SelectedOption(String optionId) implements QuestionResponse {
        @Override
        public Map<String, Object> toMap() {
            return Map.of("optionId", optionId);
        }
    }

    record SelectedOptions(List<String> optionIds) implements QuestionResponse {
        @Override
        public Map<String, Object> toMap() {
            return Map.of("optionIds", optionIds);
        }
    }

    record BooleanValue(boolean value) implements QuestionResponse {
        @Override
        public Map<String, Object> toMap() {
            return Map.of("value", value);
        }
    }

    record Text(String text) implements QuestionResponse {
        @Override
        public Map<String, Object> toMap() {
            return Map.of("text", text);
        }
    }

    record NumberValue(double number) implements QuestionResponse {
        @Override
        public Map<String, Object> toMap() {
            return Map.of("number", number);
        }
    }

    /** Эссе: блочный документ (санитизируется прикладным слоем) и файлы. */
    record EssayText(Map<String, Object> essay, List<UUID> fileIds) implements QuestionResponse {
        @Override
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("essay", essay);
            map.put("fileIds", fileIds.stream().map(UUID::toString).toList());
            return map;
        }
    }

    /** promptId (id пары) → выбранный текст ответа. */
    record Matches(Map<String, String> matches) implements QuestionResponse {
        @Override
        public Map<String, Object> toMap() {
            return Map.of("matches", matches);
        }
    }

    record Order(List<String> order) implements QuestionResponse {
        @Override
        public Map<String, Object> toMap() {
            return Map.of("order", order);
        }
    }

    /**
     * Разбор ответа по форме (без сверки с типом вопроса — несовпадение оценивается в 0).
     * @throws ValidationException field {@code response}
     */
    static QuestionResponse parse(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            throw invalid("Response is required");
        }
        String field = "response";
        if (raw.containsKey("optionId")) {
            return new SelectedOption(limited(MapValues.string(raw, "optionId", field + ".optionId")));
        }
        if (raw.containsKey("optionIds")) {
            return new SelectedOptions(strings(MapValues.list(raw, "optionIds", field + ".optionIds"), field + ".optionIds"));
        }
        if (raw.containsKey("value")) {
            return new BooleanValue(Boolean.TRUE.equals(MapValues.bool(raw, "value", field + ".value")));
        }
        if (raw.containsKey("text")) {
            return new Text(limited(MapValues.string(raw, "text", field + ".text")));
        }
        if (raw.containsKey("number")) {
            Double number = MapValues.number(raw, "number", field + ".number");
            return number == null ? invalidResponse() : new NumberValue(number);
        }
        return parseStructured(raw, field);
    }

    private static QuestionResponse parseStructured(Map<String, Object> raw, String field) {
        if (raw.containsKey("essay")) {
            Map<String, Object> essay = MapValues.object(raw, "essay", field + ".essay");
            List<String> ids = strings(MapValues.list(raw, "fileIds", field + ".fileIds"), field + ".fileIds");
            List<UUID> fileIds = ids.stream().map(id -> MapValues.parseUuid(id, field + ".fileIds")).toList();
            return new EssayText(essay == null ? Map.of() : essay, fileIds);
        }
        if (raw.containsKey("matches")) {
            Map<String, Object> matches = MapValues.object(raw, "matches", field + ".matches");
            return new Matches(stringMap(matches == null ? Map.of() : matches, field + ".matches"));
        }
        if (raw.containsKey("order")) {
            return new Order(strings(MapValues.list(raw, "order", field + ".order"), field + ".order"));
        }
        return invalidResponse();
    }

    private static List<String> strings(List<?> raw, String field) {
        if (raw.size() > MAX_ITEMS) {
            throw ValidationException.single(field, "too_many", "Too many entries");
        }
        List<String> values = new ArrayList<>();
        raw.forEach(element -> values.add(limited(MapValues.asString(element, field))));
        return List.copyOf(values);
    }

    private static Map<String, String> stringMap(Map<String, Object> raw, String field) {
        if (raw.size() > MAX_ITEMS) {
            throw ValidationException.single(field, "too_many", "Too many entries");
        }
        Map<String, String> values = new LinkedHashMap<>();
        raw.forEach((key, value) -> values.put(limited(key), limited(MapValues.asString(value, field))));
        return values;
    }

    private static String limited(String value) {
        if (value == null) {
            throw invalid("Value is required");
        }
        if (value.length() > MAX_TEXT) {
            throw ValidationException.single("response", "too_long", "Maximum length is " + MAX_TEXT);
        }
        return value;
    }

    private static QuestionResponse invalidResponse() {
        throw invalid("Unsupported response shape");
    }

    private static ValidationException invalid(String message) {
        return ValidationException.single("response", "invalid", message);
    }
}
