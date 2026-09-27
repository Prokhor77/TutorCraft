package com.tutorcraft.core.shared.api;

import com.tutorcraft.core.shared.domain.ConflictException;
import com.tutorcraft.core.shared.domain.ValidationException;

/** Оптимистичная блокировка (API-06): заголовок If-Match: "<version>" или поле version в теле. */
public final class IfMatch {

    public static final String HEADER = "If-Match";
    public static final String VERSION_CONFLICT_CODE = "conflict.version";

    private IfMatch() {
    }

    /** Версия из заголовка If-Match (кавычки и weak-префикс допускаются) либо из тела запроса. */
    public static long resolve(String header, Long bodyVersion) {
        if (header != null && !header.isBlank()) {
            return parse(header);
        }
        if (bodyVersion != null) {
            return bodyVersion;
        }
        throw ValidationException.single("version", "required", "If-Match header or version is required");
    }

    public static void check(long expected, long actual) {
        if (expected != actual) {
            throw new ConflictException(VERSION_CONFLICT_CODE, "Resource was modified by someone else");
        }
    }

    public static String etag(long version) {
        return "\"" + version + "\"";
    }

    private static long parse(String header) {
        String cleaned = header.replace("W/", "").replace("\"", "").trim();
        try {
            return Long.parseLong(cleaned);
        } catch (NumberFormatException e) {
            throw ValidationException.single(HEADER, "invalid", "If-Match must contain a numeric version");
        }
    }
}
