package com.tutorcraft.core.files.application;

import java.util.List;
import java.util.UUID;

/** Payload событий Kafka с media-worker (docs/events/README.md). */
public final class MediaEvents {

    public static final String VIDEO_UPLOADED_TYPE = "media.video-uploaded";

    private MediaEvents() {
    }

    public record VideoUploaded(UUID fileId, String bucket, String objectKey, String contentType, long sizeBytes,
                                UUID ownerUserId) {
    }

    public record VideoProcessed(UUID fileId, String status, String hlsPrefix, String masterPlaylistKey, Integer durationSec,
                                 List<Rendition> renditions, String error) {
    }

    public record Rendition(String name, Integer height, Long bandwidth) {
    }
}
