package com.tutorcraft.core.files.application;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Порт объектного хранилища (S3/MinIO или локальный диск сервера). */
public interface ObjectStorage {

    /**
     * Pre-signed PUT; клиент обязан отправить заголовок Content-Type ровно с этим значением.
     * {@code sizeBytes} — заявленный размер: реализация может ограничить им приём тела.
     */
    PresignedUrl presignUpload(String key, String contentType, long sizeBytes, Duration ttl);

    /** Pre-signed GET; attachment=true — Content-Disposition: attachment (небезопасные типы, NFR-SEC-05). */
    PresignedUrl presignDownload(String key, String fileName, String contentType, boolean attachment, Duration ttl);

    Optional<Long> sizeOf(String key);

    /** Первые {@code bytes} байт объекта (или меньше, если объект короче). */
    byte[] readPrefix(String key, int bytes);

    InputStream open(String key);

    void delete(String key);

    /** Публичный URL объекта (только для префиксов с анонимным чтением, см. ADR-008). */
    String publicUrl(String key);

    String bucket();

    record PresignedUrl(String url, Instant expiresAt) {
    }
}
