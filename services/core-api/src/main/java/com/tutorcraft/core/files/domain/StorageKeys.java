package com.tutorcraft.core.files.domain;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

/** Ключ объекта в S3: {@code t/{tenantId}/{yyyy}/{mm}/{fileId}} (имя файла в ключ не попадает). */
public final class StorageKeys {

    private static final String TEMPLATE = "t/%s/%04d/%02d/%s";

    private StorageKeys() {
    }

    public static String of(UUID tenantId, Instant createdAt, UUID fileId) {
        ZonedDateTime at = createdAt.atZone(ZoneOffset.UTC);
        return TEMPLATE.formatted(tenantId, at.getYear(), at.getMonthValue(), fileId);
    }
}
