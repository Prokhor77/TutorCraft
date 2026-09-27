package com.tutorcraft.core.shared.outbox;

/** Имена топиков Kafka — контракт с Go-сервисами (docs/events/README.md). */
public final class Topics {

    public static final String VIDEO_UPLOADED = "tc.media.video-uploaded.v1";
    public static final String VIDEO_PROCESSED = "tc.media.video-processed.v1";
    public static final String NOTIFY_REQUESTED = "tc.notify.requested.v1";
    public static final String NOTIFY_DELIVERED = "tc.notify.delivered.v1";
    public static final String TELEGRAM_LINKED = "tc.telegram.linked.v1";
    public static final String DOMAIN_EVENTS = "tc.domain.events.v1";

    private Topics() {
    }
}
