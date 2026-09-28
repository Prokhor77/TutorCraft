package com.tutorcraft.core.files.application;

import com.tutorcraft.core.files.application.ObjectStorage.PresignedUrl;
import com.tutorcraft.core.files.domain.FileNames;
import com.tutorcraft.core.files.domain.FilePurpose;
import com.tutorcraft.core.files.domain.FileStatus;
import com.tutorcraft.core.files.domain.MimeTypes;
import com.tutorcraft.core.files.domain.StorageKeys;
import com.tutorcraft.core.files.domain.StoredFile;
import com.tutorcraft.core.org.OrgApi;
import com.tutorcraft.core.shared.config.AppProperties;
import com.tutorcraft.core.shared.domain.BusinessRuleException;
import com.tutorcraft.core.shared.domain.Ids;
import com.tutorcraft.core.shared.domain.ValidationException;
import com.tutorcraft.core.shared.domain.Validator;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Начало загрузки: проверка назначения, типа, размера и квоты; запись pending и pre-signed PUT (architecture.md §5.2).
 * Загрузка доступна любому аутентифицированному пользователю: использование файла (привязка к элементу, сдаче и т.п.)
 * авторизует модуль-владелец.
 */
@Service
public class UploadService {

    private static final Logger log = LoggerFactory.getLogger(UploadService.class);
    private static final int MAX_RAW_NAME_LENGTH = 255;
    private static final long BYTES_PER_MB = 1024L * 1024;

    private final FileRepository files;
    private final OrgApi org;
    private final ObjectStorage storage;
    private final CurrentUserProvider currentUser;
    private final Clock clock;
    private final Duration uploadTtl;
    private final long maxVideoBytes;

    public UploadService(FileRepository files, OrgApi org, ObjectStorage storage, CurrentUserProvider currentUser, Clock clock,
                         AppProperties properties) {
        this.files = files;
        this.org = org;
        this.storage = storage;
        this.currentUser = currentUser;
        this.clock = clock;
        this.uploadTtl = properties.storage().uploadUrlTtl();
        this.maxVideoBytes = properties.storage().maxFileSizeBytes();
    }

    @Transactional
    public UploadTicket createUpload(UploadRequest request) {
        CurrentUser user = currentUser.require();
        FilePurpose purpose = FilePurpose.find(request.purpose())
                .orElseThrow(() -> ValidationException.single("purpose", "invalid", "Unknown purpose"));
        String contentType = MimeTypes.normalize(request.contentType());
        validate(request, purpose, contentType);
        ensureQuota(user.tenantId(), request.size());
        Instant now = clock.instant();
        UUID fileId = Ids.newId();
        String key = StorageKeys.of(user.tenantId(), now, fileId);
        files.insert(new StoredFile(fileId, user.tenantId(), user.userId(), FileNames.sanitize(request.fileName()),
                request.size(), contentType, contentType, purpose, FileStatus.PENDING, key, null, now));
        PresignedUrl url = storage.presignUpload(key, contentType, request.size(), uploadTtl);
        log.info("Upload {} created: purpose {}, {} bytes", fileId, purpose.key(), request.size());
        return new UploadTicket(fileId, url.url(), Map.of(HttpHeaders.CONTENT_TYPE, contentType), url.expiresAt());
    }

    private void validate(UploadRequest request, FilePurpose purpose, String contentType) {
        new Validator()
            .notBlank(request.fileName(), "fileName")
            .maxLength(request.fileName(), MAX_RAW_NAME_LENGTH, "fileName")
            .check(contentType != null && purpose.allows(contentType), "contentType", "not_allowed",
                    "This file type is not allowed for " + purpose.key())
            .check(request.size() > 0, "size", "invalid", "Size must be positive")
            .check(request.size() <= purpose.maxBytes(maxVideoBytes), "size", "too_large",
                    "Maximum size is " + purpose.maxBytes(maxVideoBytes) / BYTES_PER_MB + " MB")
            .throwIfInvalid();
    }

    private void ensureQuota(UUID tenantId, long size) {
        org.storageQuotaMb(tenantId).map(megabytes -> megabytes * BYTES_PER_MB).ifPresent(quota -> {
            if (files.usedBytes(tenantId) + size > quota) {
                throw new BusinessRuleException(FilesErrors.QUOTA_EXCEEDED, "Storage quota exceeded");
            }
        });
    }

    public record UploadRequest(String fileName, String contentType, long size, String purpose) {
    }

    public record UploadTicket(UUID fileId, String uploadUrl, Map<String, String> headers, Instant expiresAt) {
    }
}
