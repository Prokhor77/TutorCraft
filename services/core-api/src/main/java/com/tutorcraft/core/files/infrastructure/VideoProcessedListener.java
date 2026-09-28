package com.tutorcraft.core.files.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutorcraft.core.files.application.MediaEvents.VideoProcessed;
import com.tutorcraft.core.files.application.VideoProcessingService;
import com.tutorcraft.core.shared.outbox.EventEnvelope;
import com.tutorcraft.core.shared.outbox.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Консьюмер tc.media.video-processed.v1 (media-worker → core-api). */
@Component
class VideoProcessedListener {

    private static final Logger log = LoggerFactory.getLogger(VideoProcessedListener.class);
    private static final TypeReference<EventEnvelope<VideoProcessed>> ENVELOPE = new TypeReference<>() { };

    private final ObjectMapper objectMapper;
    private final VideoProcessingService videos;

    VideoProcessedListener(ObjectMapper objectMapper, VideoProcessingService videos) {
        this.objectMapper = objectMapper;
        this.videos = videos;
    }

    @KafkaListener(topics = Topics.VIDEO_PROCESSED)
    void onMessage(String message) {
        EventEnvelope<VideoProcessed> envelope = parse(message);
        if (envelope == null || !supported(envelope) || envelope.eventId() == null || envelope.tenantId() == null
                || envelope.payload() == null || envelope.payload().fileId() == null) {
            return;
        }
        videos.onProcessed(envelope.eventId(), envelope.tenantId(), envelope.payload());
    }

    private boolean supported(EventEnvelope<?> envelope) {
        if (envelope.supportedVersion()) {
            return true;
        }
        log.warn("{} event {} skipped: unsupported envelope version {}", Topics.VIDEO_PROCESSED, envelope.eventId(), envelope.version());
        return false;
    }

    /** Нечитаемое сообщение не станет читаемым при повторе — логируем и пропускаем. */
    private EventEnvelope<VideoProcessed> parse(String message) {
        try {
            return objectMapper.readValue(message, ENVELOPE);
        } catch (JsonProcessingException e) {
            log.error("Malformed {} message skipped: {}", Topics.VIDEO_PROCESSED, e.getClass().getSimpleName());
            return null;
        }
    }
}
