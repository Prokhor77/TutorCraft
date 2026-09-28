package com.tutorcraft.core.files.web;

import static com.tutorcraft.core.files.application.DirectObjectTransfer.PARAM_ATTACHMENT;
import static com.tutorcraft.core.files.application.DirectObjectTransfer.PARAM_CONTENT_TYPE;
import static com.tutorcraft.core.files.application.DirectObjectTransfer.PARAM_EXPIRES;
import static com.tutorcraft.core.files.application.DirectObjectTransfer.PARAM_FILE_NAME;
import static com.tutorcraft.core.files.application.DirectObjectTransfer.PARAM_SIGNATURE;
import static com.tutorcraft.core.files.application.DirectObjectTransfer.PARAM_SIZE;
import static com.tutorcraft.core.files.application.DirectObjectTransfer.PATH_PREFIX;

import com.tutorcraft.core.files.application.DirectObjectTransfer;
import com.tutorcraft.core.files.application.DirectObjectTransfer.DownloadGrant;
import com.tutorcraft.core.files.application.DirectObjectTransfer.StoredObject;
import com.tutorcraft.core.files.application.DirectObjectTransfer.UploadGrant;
import com.tutorcraft.core.files.application.FilesErrors;
import com.tutorcraft.core.shared.domain.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Шлюз объектов для STORAGE_DRIVER=local: {@code PUT/GET /storage/{key}} по подписанным ссылкам (вместо
 * pre-signed URL S3). Без JWT — доступ даёт подпись ссылки; при driver=s3 эндпоинт отвечает 404.
 * Range-запросы (перемотка видео) обрабатывает Spring MVC для тела-{@link Resource}.
 */
@RestController
class StorageController {

    private static final String MAPPING = PATH_PREFIX + "**";
    private static final Duration IMMUTABLE_MAX_AGE = Duration.ofDays(365);

    private final ObjectProvider<DirectObjectTransfer> transfers;

    StorageController(ObjectProvider<DirectObjectTransfer> transfers) {
        this.transfers = transfers;
    }

    @PutMapping(MAPPING)
    ResponseEntity<Void> upload(HttpServletRequest request,
                                @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
                                @RequestParam(value = PARAM_SIZE, required = false) Long size,
                                @RequestParam(value = PARAM_EXPIRES, required = false) Long expires,
                                @RequestParam(value = PARAM_SIGNATURE, required = false) String signature) {
        UploadGrant grant = new UploadGrant(contentType, size, expires, signature);
        try {
            transfer().receive(keyOf(request), grant, request.getContentLengthLong(), request.getInputStream());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read upload body", e);
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping(MAPPING)
    ResponseEntity<Resource> download(HttpServletRequest request,
                                      @RequestParam(value = PARAM_CONTENT_TYPE, required = false) String contentType,
                                      @RequestParam(value = PARAM_FILE_NAME, required = false) String fileName,
                                      @RequestParam(value = PARAM_ATTACHMENT, required = false) String attachment,
                                      @RequestParam(value = PARAM_EXPIRES, required = false) Long expires,
                                      @RequestParam(value = PARAM_SIGNATURE, required = false) String signature) {
        boolean asAttachment = DirectObjectTransfer.ATTACHMENT_FLAG.equals(attachment);
        DownloadGrant grant = new DownloadGrant(contentType, fileName, asAttachment, expires, signature);
        StoredObject object = transfer().read(keyOf(request), grant);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(object.contentType()))
                .cacheControl(object.immutable() ? CacheControl.maxAge(IMMUTABLE_MAX_AGE).cachePublic()
                        : CacheControl.noStore().cachePrivate());
        if (object.contentDisposition() != null) {
            response.header(HttpHeaders.CONTENT_DISPOSITION, object.contentDisposition());
        }
        return response.body(object.content());
    }

    private DirectObjectTransfer transfer() {
        DirectObjectTransfer transfer = transfers.getIfAvailable();
        if (transfer == null) {
            throw new NotFoundException(FilesErrors.NOT_FOUND, "Object storage is not served by the API");
        }
        return transfer;
    }

    /** Ключ — остаток пути после /storage/ (в ключах только безопасные символы, декодирование не требуется). */
    private static String keyOf(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.length() > PATH_PREFIX.length() ? path.substring(PATH_PREFIX.length()) : "";
    }
}
