package com.tutorcraft.core.assessment.quiz.domain;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/** Плоский текст блочного документа (для поля {@code feedback} результата и подсчёта слов эссе). */
public final class BlockText {

    private static final String PARAGRAPH_SEPARATOR = "\n";
    private static final int MAX_DEPTH = 8;

    private BlockText() {
    }

    public static String of(Map<String, Object> doc) {
        if (doc == null) {
            return "";
        }
        StringJoiner joiner = new StringJoiner(PARAGRAPH_SEPARATOR);
        collect(doc.get("blocks"), joiner, 0);
        return joiner.toString();
    }

    private static void collect(Object node, StringJoiner joiner, int depth) {
        if (depth > MAX_DEPTH) {
            return;
        }
        if (node instanceof List<?> list) {
            list.forEach(child -> collect(child, joiner, depth + 1));
            return;
        }
        if (!(node instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("text") instanceof String text && !text.isBlank()) {
            joiner.add(text);
        }
        map.forEach((key, value) -> {
            if (value instanceof List<?> || (value instanceof Map<?, ?> && !"marks".equals(key))) {
                collect(value, joiner, depth + 1);
            }
        });
    }
}
