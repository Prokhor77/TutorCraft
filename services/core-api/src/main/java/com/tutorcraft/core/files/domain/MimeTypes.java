package com.tutorcraft.core.files.domain;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Справочник MIME-типов: допустимые наборы, совместимость заявленного и определённого по сигнатуре типа. */
public final class MimeTypes {

    public static final String PDF = "application/pdf";
    public static final String ZIP = "application/zip";
    public static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    public static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final String PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation";
    public static final String PNG = "image/png";
    public static final String JPEG = "image/jpeg";
    public static final String GIF = "image/gif";
    public static final String WEBP = "image/webp";
    public static final String MP4 = "video/mp4";
    public static final String QUICKTIME = "video/quicktime";
    public static final String WEBM = "video/webm";
    public static final String MATROSKA = "video/x-matroska";
    public static final String MP3 = "audio/mpeg";
    public static final String M4A = "audio/mp4";
    public static final String OGG = "audio/ogg";
    public static final String WAV = "audio/wav";
    public static final String TEXT = "text/plain";
    public static final String CSV = "text/csv";

    static final long MB_5 = 5L * 1024 * 1024;
    static final long MB_100 = 100L * 1024 * 1024;
    static final long CONFIGURED_LIMIT = -1;

    static final Set<String> IMAGES = Set.of(PNG, JPEG, GIF, WEBP);
    static final Set<String> VIDEOS = Set.of(MP4, QUICKTIME, WEBM, MATROSKA);
    static final Set<String> SPREADSHEETS = Set.of(CSV, TEXT, XLSX);
    static final Set<String> DOCUMENTS_AND_MEDIA = union(IMAGES, VIDEOS,
            Set.of(PDF, ZIP, DOCX, XLSX, PPTX, TEXT, CSV, MP3, M4A, OGG, WAV));

    /** Типы, которые браузер может исполнить: отдаются только как attachment (NFR-SEC-05). */
    private static final Set<String> UNSAFE = Set.of("text/html", "application/xhtml+xml", "image/svg+xml", "application/xml",
            "text/xml", "application/javascript", "text/javascript");

    /** Определённый по сигнатуре тип → заявленные типы, которые ему соответствуют. */
    private static final Map<String, Set<String>> COMPATIBLE = Map.of(
            ZIP, Set.of(ZIP, DOCX, XLSX, PPTX),
            TEXT, Set.of(TEXT, CSV),
            MP4, Set.of(MP4, M4A, QUICKTIME),
            QUICKTIME, Set.of(QUICKTIME, MP4),
            WEBM, Set.of(WEBM, MATROSKA),
            OGG, Set.of(OGG),
            MP3, Set.of(MP3),
            WAV, Set.of(WAV));

    private MimeTypes() {
    }

    /** Нижний регистр, без параметров ({@code text/csv; charset=utf-8} → {@code text/csv}). */
    public static String normalize(String contentType) {
        if (contentType == null) {
            return null;
        }
        int separator = contentType.indexOf(';');
        String base = separator < 0 ? contentType : contentType.substring(0, separator);
        return base.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean compatible(String declared, String detected) {
        if (declared == null || detected == null) {
            return false;
        }
        return declared.equals(detected) || COMPATIBLE.getOrDefault(detected, Set.of()).contains(declared);
    }

    public static boolean isUnsafe(String mime) {
        return mime == null || UNSAFE.contains(mime);
    }

    @SafeVarargs
    private static Set<String> union(Set<String>... sets) {
        Set<String> all = new HashSet<>();
        for (Set<String> set : sets) {
            all.addAll(set);
        }
        return Set.copyOf(all);
    }
}
