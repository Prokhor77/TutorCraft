package com.tutorcraft.core.files.domain;

import java.time.Instant;
import java.util.UUID;

/** Метаданные файла. storageKey может совпадать у нескольких файлов после дедупликации по SHA-256. */
public record StoredFile(UUID id, UUID tenantId, UUID uploadedBy, String name, long sizeBytes, String declaredMime,
                         String mime, FilePurpose purpose, FileStatus status, String storageKey, String sha256,
                         Instant createdAt) {

    public boolean isReady() {
        return status == FileStatus.READY;
    }

    public boolean isVideo() {
        return purpose == FilePurpose.VIDEO;
    }

    public boolean uploadedBy(UUID userId) {
        return uploadedBy.equals(userId);
    }
}
