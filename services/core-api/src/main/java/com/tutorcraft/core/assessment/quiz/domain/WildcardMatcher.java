package com.tutorcraft.core.assessment.quiz.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/** Сопоставление короткого ответа с шаблоном: {@code *} — любой (в т.ч. пустой) набор символов, остальное — буквально. */
public final class WildcardMatcher {

    private static final String WILDCARD = "*";
    private static final String ANY = ".*";

    private WildcardMatcher() {
    }

    public static boolean matches(String pattern, String answer, boolean caseSensitive) {
        if (pattern == null || answer == null) {
            return false;
        }
        String normalizedPattern = normalize(pattern, caseSensitive);
        String normalizedAnswer = normalize(answer, caseSensitive);
        return toRegex(normalizedPattern).matcher(normalizedAnswer).matches();
    }

    private static String normalize(String value, boolean caseSensitive) {
        String stripped = value.strip();
        return caseSensitive ? stripped : stripped.toLowerCase(Locale.ROOT);
    }

    private static Pattern toRegex(String pattern) {
        StringBuilder regex = new StringBuilder();
        String[] literals = pattern.split(Pattern.quote(WILDCARD), -1);
        for (int i = 0; i < literals.length; i++) {
            if (i > 0) {
                regex.append(ANY);
            }
            regex.append(Pattern.quote(literals[i]));
        }
        return Pattern.compile(regex.toString(), Pattern.DOTALL);
    }
}
