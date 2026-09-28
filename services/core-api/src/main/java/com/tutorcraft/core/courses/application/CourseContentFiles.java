package com.tutorcraft.core.courses.application;

import com.tutorcraft.core.files.FilesApi;
import com.tutorcraft.core.shared.content.BlockDocs;
import com.tutorcraft.core.shared.content.BlockDocs.SanitizedDoc;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Контент и файлы курса: санитизация блочных документов с белым списком встраиваний tenant (NFR-SEC-03)
 * и связывание упомянутых файлов с владельцем (FileLink), чтобы права на файл следовали правам на курс/элемент.
 */
@Component
class CourseContentFiles {

    static final String OWNER_ITEM = "item";
    static final String OWNER_COURSE = "course";
    private static final String READY = "ready";

    private final FilesApi files;
    private final EmbedWhitelistProvider whitelist;

    CourseContentFiles(FilesApi files, EmbedWhitelistProvider whitelist) {
        this.files = files;
        this.whitelist = whitelist;
    }

    Set<String> embedWhitelist(UUID tenantId) {
        return whitelist.embedWhitelist(tenantId);
    }

    /** null → пусто; иначе санитизированный документ, файлы которого готовы (иначе ValidationException). */
    Optional<SanitizedDoc> sanitizeDoc(UUID tenantId, Object doc, String field) {
        Optional<SanitizedDoc> sanitized = BlockDocs.sanitizeOptional(doc, embedWhitelist(tenantId), field);
        sanitized.ifPresent(result -> files.requireAllReady(tenantId, result.fileIds(), field));
        return sanitized;
    }

    void requireReady(UUID tenantId, Collection<UUID> fileIds, String field) {
        files.requireAllReady(tenantId, fileIds, field);
    }

    void link(UUID tenantId, Collection<UUID> fileIds, String ownerType, UUID ownerId) {
        fileIds.stream().distinct().forEach(fileId -> files.link(tenantId, fileId, ownerType, ownerId));
    }

    /** Файлы сохранённого документа (без повторной проверки). */
    static Set<UUID> docFileIds(Map<String, Object> doc) {
        return BlockDocs.referencedFileIds(doc);
    }

    Optional<String> downloadUrl(UUID tenantId, UUID fileId) {
        if (fileId == null) {
            return Optional.empty();
        }
        return files.find(tenantId, fileId).filter(file -> READY.equals(file.status())).map(files::downloadUrl);
    }

    Optional<FilesApi.VideoInfo> video(UUID tenantId, UUID fileId) {
        return fileId == null ? Optional.empty() : files.video(tenantId, fileId);
    }
}
