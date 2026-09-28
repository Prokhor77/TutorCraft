package com.tutorcraft.core.files.infrastructure;

import com.tutorcraft.core.files.application.ObjectStorage;
import com.tutorcraft.core.shared.config.AppProperties;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Component
@ConditionalOnProperty(prefix = "tutorcraft.storage", name = "driver", havingValue = "s3")
class S3ObjectStorage implements ObjectStorage {

    private static final Logger log = LoggerFactory.getLogger(S3ObjectStorage.class);
    private static final String RANGE_TEMPLATE = "bytes=0-%d";
    private static final String PATH_SEPARATOR = "/";

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;
    private final String publicBase;

    S3ObjectStorage(S3Client s3, S3Presigner presigner, AppProperties properties) {
        this.s3 = s3;
        this.presigner = presigner;
        this.bucket = properties.storage().bucket();
        this.publicBase = stripTrailingSlash(properties.storage().publicEndpoint()) + PATH_SEPARATOR + bucket + PATH_SEPARATOR;
    }

    /** Размер в подпись не входит: его сверяет завершение загрузки (HEAD), как и раньше. */
    @Override
    public PresignedUrl presignUpload(String key, String contentType, long sizeBytes, Duration ttl) {
        PutObjectRequest put = PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build();
        PresignedPutObjectRequest presigned = presigner.presignPutObject(
                PutObjectPresignRequest.builder().signatureDuration(ttl).putObjectRequest(put).build());
        return new PresignedUrl(presigned.url().toString(), presigned.expiration());
    }

    @Override
    public PresignedUrl presignDownload(String key, String fileName, String contentType, boolean attachment, Duration ttl) {
        ContentDisposition.Builder disposition = attachment ? ContentDisposition.attachment() : ContentDisposition.inline();
        GetObjectRequest get = GetObjectRequest.builder().bucket(bucket).key(key)
                .responseContentType(contentType)
                .responseContentDisposition(disposition.filename(fileName, StandardCharsets.UTF_8).build().toString())
                .build();
        PresignedGetObjectRequest presigned = presigner.presignGetObject(
                GetObjectPresignRequest.builder().signatureDuration(ttl).getObjectRequest(get).build());
        return new PresignedUrl(presigned.url().toString(), presigned.expiration());
    }

    @Override
    public Optional<Long> sizeOf(String key) {
        try {
            return Optional.ofNullable(s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build()).contentLength());
        } catch (S3Exception e) {
            if (e.statusCode() == HttpStatus.NOT_FOUND.value()) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public byte[] readPrefix(String key, int bytes) {
        GetObjectRequest request = GetObjectRequest.builder().bucket(bucket).key(key)
                .range(RANGE_TEMPLATE.formatted(bytes - 1)).build();
        return s3.getObjectAsBytes(request).asByteArray();
    }

    @Override
    public InputStream open(String key) {
        return s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
    }

    /** Удаление «по возможности»: ошибка логируется, осиротевший объект удалит lifecycle-политика бакета. */
    @Override
    public void delete(String key) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (SdkException e) {
            log.warn("Cannot delete object from storage: {}", e.getClass().getSimpleName());
        }
    }

    @Override
    public String publicUrl(String key) {
        return publicBase + key;
    }

    @Override
    public String bucket() {
        return bucket;
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith(PATH_SEPARATOR) ? url.substring(0, url.length() - 1) : url;
    }
}
