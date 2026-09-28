package com.tutorcraft.core.identity.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Политика пароля tenant (FR-AUTH-01). Возвращает коды нарушений; пустой список — пароль допустим.
 *
 * @param minLength     минимальная длина (не ниже {@link #ABSOLUTE_MIN_LENGTH})
 * @param requireDigit  требуется хотя бы одна цифра
 * @param requireLetter требуется хотя бы одна буква
 */
public record PasswordRules(int minLength, boolean requireDigit, boolean requireLetter) {

    public static final int ABSOLUTE_MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 128;
    public static final String TOO_SHORT = "password_too_short";
    public static final String TOO_LONG = "password_too_long";
    public static final String DIGIT_REQUIRED = "password_digit_required";
    public static final String LETTER_REQUIRED = "password_letter_required";

    public List<String> violations(String password) {
        String value = password == null ? "" : password;
        List<String> result = new ArrayList<>();
        if (value.length() < Math.max(minLength, ABSOLUTE_MIN_LENGTH)) {
            result.add(TOO_SHORT);
        }
        if (value.length() > MAX_LENGTH) {
            result.add(TOO_LONG);
        }
        if (requireDigit && value.chars().noneMatch(Character::isDigit)) {
            result.add(DIGIT_REQUIRED);
        }
        if (requireLetter && value.chars().noneMatch(Character::isLetter)) {
            result.add(LETTER_REQUIRED);
        }
        return result;
    }
}
