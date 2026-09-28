package com.tutorcraft.core.identity.application;

import com.tutorcraft.core.identity.domain.ImportRow;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Предпросмотры CSV-импорта (FR-USER-02): хранятся сутки, доступны только автору. */
public interface ImportPreviewRepository {

    void insert(UUID id, UUID tenantId, UUID createdBy, List<PreviewRow> rows, Instant createdAt, Instant expiresAt);

    /** Не истёкший и ещё не применённый предпросмотр автора. */
    Optional<List<PreviewRow>> findActive(UUID tenantId, UUID previewId, UUID createdBy, Instant now);

    /** @return false, если предпросмотр уже применён параллельным запросом */
    boolean markCommitted(UUID tenantId, UUID previewId, Instant now);

    /** Проверенная строка и найденный курс (для записи при commit). */
    record PreviewRow(ImportRow row, UUID courseId) {
    }
}
