package com.tutorcraft.core.files.infrastructure;

import static com.tutorcraft.core.files.application.DirectObjectTransfer.ATTACHMENT_FLAG;
import static com.tutorcraft.core.files.application.DirectObjectTransfer.INLINE_FLAG;
import static com.tutorcraft.core.files.application.DirectObjectTransfer.PARAM_ATTACHMENT;
import static com.tutorcraft.core.files.application.DirectObjectTransfer.PARAM_CONTENT_TYPE;
import static com.tutorcraft.core.files.application.DirectObjectTransfer.PARAM_EXPIRES;
import static com.tutorcraft.core.files.application.DirectObjectTransfer.PARAM_FILE_NAME;
import static com.tutorcraft.core.files.application.DirectObjectTransfer.PARAM_SIGNATURE;
import static com.tutorcraft.core.files.application.DirectObjectTransfer.PARAM_SIZE;
import static com.tutorcraft.core.files.application.DirectObjectTransfer.PATH_PREFIX;

import com.tutorcraft.core.files.application.ObjectStorage;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Временное хранилище на диске сервера (пока не подключено S3). Ссылки — относительные {@code /storage/{key}?…}
 * того же origin: в проде nginx проводит их прямо в core-api, в dev — BFF-роут Next.js. Подпись и срок действия
 * проверяет {@link LocalObjectTransfer}.
 */
@Component
@LocalStorageEnabled
class LocalObjectStorage implements ObjectStorage {

    /** Логическое имя «бакета»: уходит в событие video-uploaded, media-worker сверяет его со своим. */
    static final String BUCKET = "local";
    static final String UPLOAD_METHOD = "PUT";
    static final String DOWNLOAD_METHOD = "GET";

    private final LocalFileStore files;
    private final ObjectLinkSigner signer;
    private final Clock clock;

    LocalObjectStorage(LocalFileStore files, ObjectLinkSigner signer, Clock clock) {
        this.files = files;
        this.signer = signer;
        this.clock = clock;
    }

    @Override
    public PresignedUrl presignUpload(String key, String contentType, long sizeBytes, Duration ttl) {
        Instant expiresAt = expiry(ttl);
        long exp = expiresAt.getEpochSecond();
        Map<String, String> query = new LinkedHashMap<>();
        query.put(PARAM_SIZE, String.valueOf(sizeBytes));
        query.put(PARAM_EXPIRES, String.valueOf(exp));
        query.put(PARAM_SIGNATURE, signer.sign(UPLOAD_METHOD, key, contentType, sizeBytes, exp));
        return new PresignedUrl(url(key, query), expiresAt);
    }

    @Override
    public PresignedUrl presignDownload(String key, String fileName, String contentType, boolean attachment, Duration ttl) {
        Instant expiresAt = expiry(ttl);
        long exp = expiresAt.getEpochSecond();
        String disposition = attachment ? ATTACHMENT_FLAG : INLINE_FLAG;
        Map<String, String> query = new LinkedHashMap<>();
        query.put(PARAM_CONTENT_TYPE, contentType);
        query.put(PARAM_FILE_NAME, fileName);
        query.put(PARAM_ATTACHMENT, disposition);
        query.put(PARAM_EXPIRES, String.valueOf(exp));
        query.put(PARAM_SIGNATURE, signer.sign(DOWNLOAD_METHOD, key, contentType, fileName, disposition, exp));
        return new PresignedUrl(url(key, query), expiresAt);
    }

    @Override
    public Optional<Long> sizeOf(String key) {
        return files.sizeOf(key);
    }

    @Override
    public byte[] readPrefix(String key, int bytes) {
        return files.readPrefix(key, bytes);
    }

    @Override
    public InputStream open(String key) {
        return files.open(key);
    }

    @Override
    public void delete(String key) {
        files.delete(key);
    }

    @Override
    public String publicUrl(String key) {
        return PATH_PREFIX + key;
    }

    @Override
    public String bucket() {
        return BUCKET;
    }

    private Instant expiry(Duration ttl) {
        return clock.instant().plus(ttl);
    }

    private static String url(String key, Map<String, String> query) {
        return PATH_PREFIX + key + "?" + query.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }
}
