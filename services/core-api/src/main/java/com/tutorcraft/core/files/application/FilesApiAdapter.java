package com.tutorcraft.core.files.application;

import com.tutorcraft.core.files.FilesApi;
import com.tutorcraft.core.files.domain.StoredFile;
import com.tutorcraft.core.shared.domain.FieldViolation;
import com.tutorcraft.core.shared.domain.NotFoundException;
import com.tutorcraft.core.shared.domain.ValidationException;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Реализация FilesApi: только репозиторий и хранилище (правило против циклов бинов). */
@Component
class FilesApiAdapter implements FilesApi {

    private final FileRepository files;
    private final FileUrls urls;
    private final Clock clock;

    FilesApiAdapter(FileRepository files, FileUrls urls, Clock clock) {
        this.files = files;
        this.urls = urls;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public FileRef requireReady(UUID tenantId, UUID fileId, String field) {
        return files.find(tenantId, fileId)
                .filter(StoredFile::isReady)
                .map(FilesApiAdapter::toRef)
                .orElseThrow(() -> notReady(field));
    }

    @Override
    @Transactional(readOnly = true)
    public List<FileRef> requireAllReady(UUID tenantId, Collection<UUID> fileIds, String field) {
        if (fileIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, StoredFile> found = files.findAll(tenantId, fileIds).stream()
                .filter(StoredFile::isReady)
                .collect(Collectors.toMap(StoredFile::id, Function.identity()));
        if (!found.keySet().containsAll(fileIds)) {
            throw notReady(field);
        }
        return fileIds.stream().distinct().map(found::get).map(FilesApiAdapter::toRef).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FileRef> find(UUID tenantId, UUID fileId) {
        return files.find(tenantId, fileId).map(FilesApiAdapter::toRef);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FileRef> findAll(UUID tenantId, Collection<UUID> fileIds) {
        if (fileIds.isEmpty()) {
            return List.of();
        }
        return files.findAll(tenantId, fileIds).stream().map(FilesApiAdapter::toRef).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public String downloadUrl(FileRef file) {
        return files.find(file.tenantId(), file.id())
                .map(urls::download)
                .orElseThrow(() -> new NotFoundException(FilesErrors.NOT_FOUND, "File not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VideoInfo> video(UUID tenantId, UUID fileId) {
        return files.findVideo(tenantId, fileId).map(urls::video);
    }

    @Override
    @Transactional
    public void link(UUID tenantId, UUID fileId, String ownerType, UUID ownerId) {
        if (files.find(tenantId, fileId).isEmpty()) {
            throw new NotFoundException(FilesErrors.NOT_FOUND, "File not found");
        }
        files.insertLink(tenantId, fileId, ownerType, ownerId, clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public List<LinkedFileSize> linkedFileSizes(UUID tenantId, String ownerType) {
        return files.linkedFileSizes(tenantId, ownerType).stream()
                .map(row -> new LinkedFileSize(row.fileId(), row.ownerId(), row.sizeBytes(), row.hlsBytes()))
                .toList();
    }

    private static FileRef toRef(StoredFile file) {
        return new FileRef(file.id(), file.tenantId(), file.name(), file.sizeBytes(), file.mime(), file.status().key(),
                file.purpose().key(), file.uploadedBy());
    }

    private static ValidationException notReady(String field) {
        return new ValidationException(List.of(new FieldViolation(field, FilesErrors.FILE_NOT_READY_FIELD_CODE,
                "File is not uploaded or not verified")));
    }
}
