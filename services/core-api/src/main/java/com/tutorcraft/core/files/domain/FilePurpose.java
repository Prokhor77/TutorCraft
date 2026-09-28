package com.tutorcraft.core.files.domain;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Назначение загрузки: лимит размера и допустимые типы (NFR-SEC-05). Для видео лимит задаётся конфигурацией
 * ({@code storage.maxFileSizeBytes}), поэтому {@link #maxBytes(long)} принимает его параметром.
 */
public enum FilePurpose {
    CONTENT("content", MimeTypes.MB_100, MimeTypes.DOCUMENTS_AND_MEDIA),
    SUBMISSION("submission", MimeTypes.MB_100, MimeTypes.DOCUMENTS_AND_MEDIA),
    AVATAR("avatar", MimeTypes.MB_5, MimeTypes.IMAGES),
    COVER("cover", MimeTypes.MB_5, MimeTypes.IMAGES),
    VIDEO("video", MimeTypes.CONFIGURED_LIMIT, MimeTypes.VIDEOS),
    IMPORT("import", MimeTypes.MB_100, MimeTypes.SPREADSHEETS);

    private final String key;
    private final long fixedMaxBytes;
    private final Set<String> allowedTypes;

    FilePurpose(String key, long fixedMaxBytes, Set<String> allowedTypes) {
        this.key = key;
        this.fixedMaxBytes = fixedMaxBytes;
        this.allowedTypes = allowedTypes;
    }

    public String key() {
        return key;
    }

    public long maxBytes(long configuredVideoLimit) {
        return fixedMaxBytes == MimeTypes.CONFIGURED_LIMIT ? configuredVideoLimit : fixedMaxBytes;
    }

    public boolean allows(String mime) {
        return allowedTypes.contains(mime);
    }

    public static Optional<FilePurpose> find(String key) {
        return Arrays.stream(values()).filter(purpose -> purpose.key.equals(key)).findFirst();
    }
}
