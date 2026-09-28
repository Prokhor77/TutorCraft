package com.tutorcraft.core.files.infrastructure;

import com.tutorcraft.core.files.application.DirectObjectTransfer;
import com.tutorcraft.core.files.application.FilesErrors;
import com.tutorcraft.core.files.domain.MimeTypes;
import com.tutorcraft.core.shared.domain.BusinessRuleException;
import com.tutorcraft.core.shared.domain.ConflictException;
import com.tutorcraft.core.shared.domain.ForbiddenException;
import com.tutorcraft.core.shared.domain.NotFoundException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Приём и выдача объектов локального хранилища по ссылкам {@link LocalObjectStorage}: проверка подписи, срока,
 * типа и размера. Публичен только префикс {@code hls/} — плеер запрашивает сегменты по относительным путям из
 * плейлиста, подписать каждый нельзя (так же устроено публичное чтение hls/ в S3, ADR-008).
 */
@Component
@LocalStorageEnabled
class LocalObjectTransfer implements DirectObjectTransfer {

    private static final Logger log = LoggerFactory.getLogger(LocalObjectTransfer.class);
    private static final String PUBLIC_PREFIX = "hls/";
    private static final long UNKNOWN_LENGTH = -1;
    private static final Map<String, String> HLS_CONTENT_TYPES = Map.of(
            ".m3u8", "application/vnd.apple.mpegurl",
            ".ts", "video/mp2t",
            ".m4s", "video/iso.segment",
            ".mp4", "video/mp4",
            ".aac", "audio/aac",
            ".vtt", "text/vtt");

    private final LocalFileStore files;
    private final ObjectLinkSigner signer;
    private final Clock clock;

    LocalObjectTransfer(LocalFileStore files, ObjectLinkSigner signer, Clock clock) {
        this.files = files;
        this.signer = signer;
        this.clock = clock;
    }

    @Override
    public void receive(String key, UploadGrant grant, long contentLength, InputStream body) {
        requireValidKey(key);
        String contentType = MimeTypes.normalize(grant.contentType());
        requireFresh(grant.expiresAt());
        if (grant.sizeBytes() == null || !signer.verify(grant.signature(), LocalObjectStorage.UPLOAD_METHOD, key, contentType,
                grant.sizeBytes(), grant.expiresAt())) {
            throw invalidLink();
        }
        long declared = grant.sizeBytes();
        if (contentLength != UNKNOWN_LENGTH && contentLength != declared) {
            throw sizeMismatch();
        }
        if (files.existing(key).isPresent()) {
            throw new ConflictException(FilesErrors.ALREADY_UPLOADED, "Object has already been uploaded");
        }
        if (!files.writeExactly(key, body, declared)) {
            throw sizeMismatch();
        }
        log.info("Object stored locally: {} bytes", declared);
    }

    @Override
    public StoredObject read(String key, DownloadGrant grant) {
        requireValidKey(key);
        if (grant.isEmpty()) {
            return readPublic(key);
        }
        requireFresh(grant.expiresAt());
        String disposition = grant.attachment() ? ATTACHMENT_FLAG : INLINE_FLAG;
        if (!signer.verify(grant.signature(), LocalObjectStorage.DOWNLOAD_METHOD, key, grant.contentType(), grant.fileName(),
                disposition, grant.expiresAt())) {
            throw invalidLink();
        }
        return new StoredObject(resource(key), grant.contentType(), contentDisposition(grant), false);
    }

    private StoredObject readPublic(String key) {
        if (!key.startsWith(PUBLIC_PREFIX)) {
            throw invalidLink();
        }
        return new StoredObject(resource(key), hlsContentType(key), null, true);
    }

    private FileSystemResource resource(String key) {
        Path path = files.existing(key).orElseThrow(() -> new NotFoundException(FilesErrors.NOT_FOUND, "Object not found"));
        return new FileSystemResource(path);
    }

    private void requireFresh(Long expiresAt) {
        if (expiresAt == null) {
            throw invalidLink();
        }
        if (clock.instant().getEpochSecond() > expiresAt) {
            throw new ForbiddenException(FilesErrors.LINK_EXPIRED, "Link has expired");
        }
    }

    private static void requireValidKey(String key) {
        if (!LocalFileStore.isValidKey(key)) {
            throw new NotFoundException(FilesErrors.NOT_FOUND, "Object not found");
        }
    }

    private static String contentDisposition(DownloadGrant grant) {
        ContentDisposition.Builder builder = grant.attachment() ? ContentDisposition.attachment() : ContentDisposition.inline();
        return grant.fileName() == null ? builder.build().toString()
                : builder.filename(grant.fileName(), StandardCharsets.UTF_8).build().toString();
    }

    private static String hlsContentType(String key) {
        int dot = key.lastIndexOf('.');
        String extension = dot < 0 ? "" : key.substring(dot).toLowerCase(Locale.ROOT);
        return HLS_CONTENT_TYPES.getOrDefault(extension, MediaType.APPLICATION_OCTET_STREAM_VALUE);
    }

    private static ForbiddenException invalidLink() {
        return new ForbiddenException(FilesErrors.LINK_INVALID, "Invalid link");
    }

    private static BusinessRuleException sizeMismatch() {
        return new BusinessRuleException(FilesErrors.SIZE_MISMATCH, "Body size does not match the declared size");
    }
}
