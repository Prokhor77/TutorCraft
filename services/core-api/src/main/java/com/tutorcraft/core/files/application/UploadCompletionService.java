package com.tutorcraft.core.files.application;

import com.tutorcraft.core.files.application.MediaEvents.VideoUploaded;
import com.tutorcraft.core.files.domain.MimeSniffer;
import com.tutorcraft.core.files.domain.MimeTypes;
import com.tutorcraft.core.files.domain.Sha256;
import com.tutorcraft.core.files.domain.StoredFile;
import com.tutorcraft.core.shared.domain.BusinessRuleException;
import com.tutorcraft.core.shared.domain.NotFoundException;
import com.tutorcraft.core.shared.outbox.OutboxPublisher;
import com.tutorcraft.core.shared.outbox.Topics;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Завершение загрузки (NFR-SEC-05): размер (HEAD), реальный тип по сигнатуре, SHA-256 и дедупликация,
 * для видео — запуск транскодирования через outbox. Сетевые проверки S3 выполняются вне транзакции БД;
 * отказ фиксируется сразу (статус rejected сохраняется, хотя клиент получает 422).
 */
@Service
public class UploadCompletionService {

    private static final Logger log = LoggerFactory.getLogger(UploadCompletionService.class);
    private static final long HASH_LIMIT_BYTES = 50L * 1024 * 1024;

    private final FileRepository files;
    private final ObjectStorage storage;
    private final CurrentUserProvider currentUser;
    private final FileViews views;
    private final OutboxPublisher outbox;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public UploadCompletionService(FileRepository files, ObjectStorage storage, CurrentUserProvider currentUser, FileViews views,
                                   OutboxPublisher outbox, PlatformTransactionManager transactionManager, Clock clock) {
        this.files = files;
        this.storage = storage;
        this.currentUser = currentUser;
        this.views = views;
        this.outbox = outbox;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public FileMetaView complete(UUID fileId) {
        CurrentUser user = currentUser.require();
        StoredFile file = files.find(user.tenantId(), fileId)
                .filter(found -> found.uploadedBy(user.userId()))
                .orElseThrow(() -> new NotFoundException(FilesErrors.NOT_FOUND, "File not found"));
        return switch (file.status()) {
            case READY -> views.view(file);
            case REJECTED -> throw new BusinessRuleException(FilesErrors.REJECTED, "File was rejected");
            case PENDING -> views.view(verifyAndFinalize(file));
        };
    }

    private StoredFile verifyAndFinalize(StoredFile file) {
        long actualSize = storage.sizeOf(file.storageKey())
                .orElseThrow(() -> new BusinessRuleException(FilesErrors.NOT_UPLOADED, "File has not been uploaded yet"));
        if (actualSize != file.sizeBytes()) {
            throw reject(file, FilesErrors.SIZE_MISMATCH);
        }
        String mime = verifiedMime(file).orElseThrow(() -> reject(file, FilesErrors.TYPE_MISMATCH));
        String sha256 = file.sizeBytes() <= HASH_LIMIT_BYTES ? hash(file) : null;
        Finalized result = transactions.execute(status -> finalizeReady(file, mime, sha256));
        if (result.orphanKey() != null) {
            storage.delete(result.orphanKey());
        }
        return result.file();
    }

    /** Сохраняется заявленный тип, если он совместим с определённым по сигнатуре (docx ↔ zip, csv ↔ text). */
    private Optional<String> verifiedMime(StoredFile file) {
        byte[] head = storage.readPrefix(file.storageKey(), MimeSniffer.HEAD_BYTES);
        return MimeSniffer.detect(head)
                .filter(detected -> MimeTypes.compatible(file.declaredMime(), detected))
                .map(detected -> file.declaredMime());
    }

    private Finalized finalizeReady(StoredFile file, String mime, String sha256) {
        Optional<StoredFile> duplicate = findDuplicate(file, sha256);
        String key = duplicate.map(StoredFile::storageKey).orElse(file.storageKey());
        Instant now = clock.instant();
        if (!files.markReady(file.tenantId(), file.id(), mime, sha256, key, now)) {
            return new Finalized(reload(file), null);
        }
        if (file.isVideo()) {
            startTranscoding(file, mime, now);
        }
        log.info("File {} is ready{}", file.id(), duplicate.isPresent() ? " (deduplicated)" : "");
        return new Finalized(reload(file), duplicate.isPresent() ? file.storageKey() : null);
    }

    /** Видео не дедуплицируются: у каждого файла своя запись транскодирования. */
    private Optional<StoredFile> findDuplicate(StoredFile file, String sha256) {
        if (sha256 == null || file.isVideo()) {
            return Optional.empty();
        }
        return files.findReadyByHash(file.tenantId(), sha256).filter(other -> !other.id().equals(file.id()));
    }

    private void startTranscoding(StoredFile file, String mime, Instant now) {
        files.insertVideo(file.tenantId(), file.id(), now);
        outbox.publish(Topics.VIDEO_UPLOADED, file.tenantId(), MediaEvents.VIDEO_UPLOADED_TYPE,
                new VideoUploaded(file.id(), storage.bucket(), file.storageKey(), mime, file.sizeBytes(), file.uploadedBy()));
    }

    private BusinessRuleException reject(StoredFile file, String code) {
        files.markRejected(file.tenantId(), file.id(), code, clock.instant());
        storage.delete(file.storageKey());
        log.info("File {} rejected: {}", file.id(), code);
        return new BusinessRuleException(code, "File failed verification");
    }

    /** Хеш нужен только для дедупликации: при ошибке чтения файл принимается без неё. */
    private String hash(StoredFile file) {
        try (InputStream input = storage.open(file.storageKey())) {
            return Sha256.hex(input);
        } catch (IOException e) {
            log.warn("Cannot hash file {}: {}", file.id(), e.getClass().getSimpleName());
            return null;
        }
    }

    private StoredFile reload(StoredFile file) {
        return files.find(file.tenantId(), file.id()).orElseThrow(() -> new IllegalStateException("File disappeared"));
    }

    private record Finalized(StoredFile file, String orphanKey) {
    }
}
