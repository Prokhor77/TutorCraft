package com.tutorcraft.core.files.domain;

import java.util.regex.Pattern;

/** Санитизация имени файла из клиента: без путей, управляющих и зарезервированных символов, ограниченная длина. */
public final class FileNames {

    public static final int MAX_LENGTH = 200;
    private static final String FALLBACK = "file";
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}\\p{Cf}]");
    private static final Pattern RESERVED = Pattern.compile("[<>:\"/\\\\|?*]");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern EDGE_DOTS_AND_SPACES = Pattern.compile("^[.\\s]+|[.\\s]+$");
    private static final char EXTENSION_SEPARATOR = '.';
    private static final int MAX_EXTENSION_LENGTH = 16;

    private FileNames() {
    }

    public static String sanitize(String raw) {
        if (raw == null) {
            return FALLBACK;
        }
        String name = lastPathSegment(raw);
        name = CONTROL.matcher(name).replaceAll("");
        name = RESERVED.matcher(name).replaceAll("_");
        name = SPACES.matcher(name).replaceAll(" ");
        name = EDGE_DOTS_AND_SPACES.matcher(name).replaceAll("");
        return name.isEmpty() ? FALLBACK : truncate(name);
    }

    private static String lastPathSegment(String raw) {
        int slash = Math.max(raw.lastIndexOf('/'), raw.lastIndexOf('\\'));
        return slash < 0 ? raw : raw.substring(slash + 1);
    }

    /** Обрезка с сохранением расширения. */
    private static String truncate(String name) {
        if (name.length() <= MAX_LENGTH) {
            return name;
        }
        int dot = name.lastIndexOf(EXTENSION_SEPARATOR);
        boolean keepExtension = dot > 0 && name.length() - dot <= MAX_EXTENSION_LENGTH;
        String extension = keepExtension ? name.substring(dot) : "";
        return name.substring(0, MAX_LENGTH - extension.length()) + extension;
    }
}
