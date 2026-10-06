package com.tutorcraft.core.files.application;

import com.tutorcraft.core.files.domain.StoredFile;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Метаданные файлов, связи с владельцами и статусы видео. Все методы фильтруют по tenant (DATA-01). */
public interface FileRepository {

    void insert(StoredFile file);

    Optional<StoredFile> find(UUID tenantId, UUID fileId);

    List<StoredFile> findAll(UUID tenantId, Collection<UUID> fileIds);

    Optional<StoredFile> findReadyByHash(UUID tenantId, String sha256);

    /** Переход pending → ready. @return false, если файл уже не pending. */
    boolean markReady(UUID tenantId, UUID fileId, String mime, String sha256, String storageKey, Instant completedAt);

    void markRejected(UUID tenantId, UUID fileId, String reason, Instant at);

    /** Сумма размеров непринятых к отказу файлов tenant (квота). */
    long usedBytes(UUID tenantId);

    /** Системный запрос по всем tenant (админка платформы): объём и число непринятых к отказу файлов по назначению. */
    List<PurposeUsageRow> usageByTenantAndPurpose();

    /** Системный запрос: оценка объёма HLS-рендишенов готовых видео по tenant (длительность × битрейт). */
    Map<UUID, Long> hlsBytesByTenant();

    /** Файлы tenant, привязанные к владельцам типа ownerType: строка на связь, с оценкой объёма HLS для видео. */
    List<LinkedFileSize> linkedFileSizes(UUID tenantId, String ownerType);

    void insertLink(UUID tenantId, UUID fileId, String ownerType, UUID ownerId, Instant at);

    List<FileLink> links(UUID tenantId, UUID fileId);

    void insertVideo(UUID tenantId, UUID fileId, Instant at);

    Optional<VideoRecord> findVideo(UUID tenantId, UUID fileId);

    /** @return false, если видео не найдено в tenant */
    boolean updateVideo(UUID tenantId, UUID fileId, VideoUpdate update, Instant at);

    record PurposeUsageRow(UUID tenantId, String purpose, long bytes, long files) {
    }

    record LinkedFileSize(UUID fileId, UUID ownerId, long sizeBytes, long hlsBytes) {
    }

    record FileLink(String ownerType, UUID ownerId) {
    }

    record VideoRecord(UUID fileId, String status, String masterPlaylistKey, Integer durationSec) {
    }

    /** renditionsJson — сериализованный список рендишенов от media-worker (jsonb). */
    record VideoUpdate(String status, String hlsPrefix, String masterPlaylistKey, Integer durationSec, String renditionsJson,
                       String error) {
    }
}
