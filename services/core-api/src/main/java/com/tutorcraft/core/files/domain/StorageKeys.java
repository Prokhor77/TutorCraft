package com.tutorcraft.core.files.domain;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Ключ объекта в S3: {@code t/{tenantId}/{yyyy}/{mm}/{fileId}} (имя файла в ключ не попадает). HLS-рендишены видео
 * media-worker пишет в {@code hls/{fileId}/} (ADR-008).
 */
public final class StorageKeys {

    private static final String TEMPLATE = "t/%s/%04d/%02d/%s";
    private static final String TENANT_PREFIX = "t/%s/";
    private static final String HLS_PREFIX = "hls/%s/";

    private StorageKeys() {
    }

    public static String of(UUID tenantId, Instant createdAt, UUID fileId) {
        ZonedDateTime at = createdAt.atZone(ZoneOffset.UTC);
        return TEMPLATE.formatted(tenantId, at.getYear(), at.getMonthValue(), fileId);
    }

    /** Все исходные загрузки tenant. */
    public static String tenantPrefix(UUID tenantId) {
        return TENANT_PREFIX.formatted(tenantId);
    }

    /** HLS-рендишены одного видео. */
    public static String hlsPrefix(UUID fileId) {
        return HLS_PREFIX.formatted(fileId);
    }
}
