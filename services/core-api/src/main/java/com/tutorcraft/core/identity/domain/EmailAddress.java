package com.tutorcraft.core.identity.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/** Нормализация и проверка формата email. Сравнение email — без учёта регистра (индекс lower(email)). */
public final class EmailAddress {

    public static final int MAX_LENGTH = 254;
    private static final Pattern FORMAT = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private EmailAddress() {
    }

    public static String normalize(String raw) {
        return raw == null ? null : raw.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isValid(String raw) {
        if (raw == null) {
            return false;
        }
        String value = raw.trim();
        return !value.isEmpty() && value.length() <= MAX_LENGTH && FORMAT.matcher(value).matches();
    }
}
