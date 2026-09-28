package com.tutorcraft.core.activity.domain;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Маскирует в свободном тексте (сообщения исключений, стеки из браузера) то, что нельзя хранить в журнале
 * (NFR-SEC-11): e-mail, Bearer/JWT, длинные непрозрачные токены, значения {@code password=...}/{@code token: ...}.
 */
public final class SensitiveDataMasker {

    static final String MASK = "***";

    private record Rule(Pattern pattern, String replacement) {
    }

    /** Порядок важен: сначала Bearer/JWT и пары ключ=значение, потом общие шаблоны. */
    private static final List<Rule> RULES = List.of(
            new Rule(Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._~+/=-]+"), "Bearer " + MASK),
            new Rule(Pattern.compile("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]*"), MASK),
            new Rule(Pattern.compile("(?i)\\b(password|passwd|secret|token|api[_-]?key)(\\s*[=:]\\s*)[^\\s,;&\"')]+"),
                    "$1$2" + MASK),
            new Rule(Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), MASK),
            new Rule(Pattern.compile("\\b[A-Za-z0-9_-]{40,}\\b"), MASK));

    private SensitiveDataMasker() {
    }

    public static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = text;
        for (Rule rule : RULES) {
            result = rule.pattern().matcher(result).replaceAll(rule.replacement());
        }
        return result;
    }

    /** Маскирует и обрезает до {@code maxLength} символов (null остаётся null). */
    public static String maskAndTruncate(String text, int maxLength) {
        return truncate(mask(text), maxLength);
    }

    public static String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }
}
