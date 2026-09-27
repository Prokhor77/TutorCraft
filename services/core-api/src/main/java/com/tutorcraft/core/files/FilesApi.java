package com.tutorcraft.core.files;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Публичный API модуля files. */
public interface FilesApi {

    /** Файл tenant в статусе ready. @throws ValidationException(field, "file_not_ready") */
    FileRef requireReady(UUID tenantId, UUID fileId, String field);

    List<FileRef> requireAllReady(UUID tenantId, Collection<UUID> fileIds, String field);

    Optional<FileRef> find(UUID tenantId, UUID fileId);

    List<FileRef> findAll(UUID tenantId, Collection<UUID> fileIds);

    /** Pre-signed GET URL (для контроллеров, уже проверивших права на владельца файла). */
    String downloadUrl(FileRef file);

    Optional<VideoInfo> video(UUID tenantId, UUID fileId);

    /** Связь файла с владельцем (FileLink): 'item', 'submission', 'feedback', 'post', 'course', 'user'. */
    void link(UUID tenantId, UUID fileId, String ownerType, UUID ownerId);

    record FileRef(UUID id, UUID tenantId, String name, long size, String mime, String status, String purpose,
                   UUID uploadedBy) {
    }

    record VideoInfo(String status, String hlsUrl, Integer durationSec) {
    }
}
