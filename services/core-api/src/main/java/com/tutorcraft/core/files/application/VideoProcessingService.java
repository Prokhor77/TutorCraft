package com.tutorcraft.core.files.application;

import com.tutorcraft.core.communication.NotificationCategory;
import com.tutorcraft.core.communication.NotificationsApi;
import com.tutorcraft.core.communication.NotificationsApi.NotificationCommand;
import com.tutorcraft.core.files.application.FileRepository.VideoUpdate;
import com.tutorcraft.core.files.application.MediaEvents.VideoProcessed;
import com.tutorcraft.core.files.domain.StoredFile;
import com.tutorcraft.core.shared.outbox.ProcessedEvents;
import com.tutorcraft.core.shared.persistence.JsonCodec;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Результат транскодирования от media-worker: статус видео и уведомление автору (architecture.md §5.2, шаг 5). */
@Service
public class VideoProcessingService {

    static final String CONSUMER = "files.video-processed";
    private static final Logger log = LoggerFactory.getLogger(VideoProcessingService.class);
    private static final String READY = "ready";
    private static final String FAILED = "failed";
    private static final String READY_MESSAGE = "files.video_ready";
    private static final String FAILED_MESSAGE = "files.video_failed";
    private static final int MAX_ERROR_LENGTH = 500;

    private final FileRepository files;
    private final ProcessedEvents processedEvents;
    private final NotificationsApi notifications;
    private final JsonCodec json;
    private final Clock clock;

    public VideoProcessingService(FileRepository files, ProcessedEvents processedEvents, NotificationsApi notifications,
                                  JsonCodec json, Clock clock) {
        this.files = files;
        this.processedEvents = processedEvents;
        this.notifications = notifications;
        this.json = json;
        this.clock = clock;
    }

    /** Идемпотентно по eventId. */
    @Transactional
    public void onProcessed(UUID eventId, UUID tenantId, VideoProcessed event) {
        if (!processedEvents.markProcessed(eventId, CONSUMER)) {
            return;
        }
        Optional<StoredFile> file = files.find(tenantId, event.fileId());
        if (file.isEmpty()) {
            log.warn("Video processed event {} refers to unknown file {}", eventId, event.fileId());
            return;
        }
        String status = READY.equals(event.status()) ? READY : FAILED;
        files.updateVideo(tenantId, event.fileId(), toUpdate(status, event), clock.instant());
        notifyUploader(file.get(), status);
        log.info("Video {} processing finished: {}", event.fileId(), status);
    }

    private VideoUpdate toUpdate(String status, VideoProcessed event) {
        String renditions = json.write(event.renditions() == null ? List.of() : event.renditions());
        return new VideoUpdate(status, event.hlsPrefix(), event.masterPlaylistKey(), event.durationSec(), renditions,
                truncate(event.error()));
    }

    private void notifyUploader(StoredFile file, String status) {
        String messageCode = READY.equals(status) ? READY_MESSAGE : FAILED_MESSAGE;
        notifications.notify(NotificationCommand.of(file.tenantId(), List.of(file.uploadedBy()), NotificationCategory.VIDEO_READY,
                messageCode, List.<Object>of(file.name()), null, messageCode + ":" + file.id()));
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() > MAX_ERROR_LENGTH ? error.substring(0, MAX_ERROR_LENGTH) : error;
    }
}
