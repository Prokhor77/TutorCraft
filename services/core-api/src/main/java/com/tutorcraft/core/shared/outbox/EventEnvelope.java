package com.tutorcraft.core.shared.outbox;

import java.time.Instant;
import java.util.UUID;

/** Конверт события Kafka (ADR-005). */
public record EventEnvelope<T>(UUID eventId, String type, int version, Instant occurredAt, UUID tenantId, T payload) {

    public static final int CURRENT_VERSION = 1;
}
