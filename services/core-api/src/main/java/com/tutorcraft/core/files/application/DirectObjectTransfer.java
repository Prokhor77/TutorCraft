package com.tutorcraft.core.files.application;

import java.io.InputStream;
import org.springframework.core.io.Resource;

/**
 * Приём и выдача объектов самим core-api — для хранилищ без собственного HTTP-шлюза (локальный диск сервера).
 * Браузер работает по тем же «pre-signed» ссылкам, что и с S3: ссылка подписана и ограничена по времени, поэтому
 * эндпоинт не требует JWT (XHR-загрузка и {@code <video src>} не умеют отправлять Authorization).
 * Объекты с публичным префиксом {@code hls/} (ADR-008) отдаются без подписи.
 */
public interface DirectObjectTransfer {

    /** Путь, под которым core-api принимает и отдаёт объекты: {@code /storage/{key}}. */
    String PATH_PREFIX = "/storage/";
    String PARAM_CONTENT_TYPE = "ct";
    String PARAM_SIZE = "size";
    String PARAM_FILE_NAME = "name";
    String PARAM_ATTACHMENT = "dl";
    String PARAM_EXPIRES = "exp";
    String PARAM_SIGNATURE = "sig";
    /** Значения {@link #PARAM_ATTACHMENT}: Content-Disposition attachment / inline. */
    String ATTACHMENT_FLAG = "1";
    String INLINE_FLAG = "0";

    /** Сохраняет тело PUT-запроса под ключом; повторная запись уже загруженного объекта запрещена. */
    void receive(String key, UploadGrant grant, long contentLength, InputStream body);

    /** Объект по подписанной ссылке на скачивание либо публичный (hls/) — тогда {@code grant} пуст. */
    StoredObject read(String key, DownloadGrant grant);

    /** Параметры подписанной ссылки на загрузку; {@code contentType} — фактический заголовок запроса. */
    record UploadGrant(String contentType, Long sizeBytes, Long expiresAt, String signature) {
    }

    /** Параметры подписанной ссылки на скачивание (все поля null — запрос публичного объекта). */
    record DownloadGrant(String contentType, String fileName, boolean attachment, Long expiresAt, String signature) {

        public boolean isEmpty() {
            return signature == null && expiresAt == null;
        }
    }

    /** {@code immutable} — объект можно кешировать надолго (сегменты HLS). */
    record StoredObject(Resource content, String contentType, String contentDisposition, boolean immutable) {
    }
}
