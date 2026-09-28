package com.tutorcraft.core.communication.notifications.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutorcraft.core.communication.notifications.application.DeliveryStatusService;
import com.tutorcraft.core.communication.notifications.application.DeliveryStatusService.DeliveryReport;
import com.tutorcraft.core.shared.outbox.EventEnvelope;
import com.tutorcraft.core.shared.outbox.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Консьюмер tc.notify.delivered.v1 (notifier → core-api). */
@Component
class DeliveryStatusListener {

    private static final Logger log = LoggerFactory.getLogger(DeliveryStatusListener.class);
    private static final TypeReference<EventEnvelope<DeliveryReport>> ENVELOPE = new TypeReference<>() { };

    private final ObjectMapper objectMapper;
    private final DeliveryStatusService deliveries;

    DeliveryStatusListener(ObjectMapper objectMapper, DeliveryStatusService deliveries) {
        this.objectMapper = objectMapper;
        this.deliveries = deliveries;
    }

    @KafkaListener(topics = Topics.NOTIFY_DELIVERED)
    void onMessage(String message) {
        EventEnvelope<DeliveryReport> envelope = parse(message);
        if (envelope == null || envelope.eventId() == null || envelope.payload() == null) {
            return;
        }
        deliveries.record(envelope.eventId(), envelope.payload());
    }

    /** Нечитаемое сообщение повторно не обработать — логируем тип ошибки (без содержимого) и пропускаем. */
    private EventEnvelope<DeliveryReport> parse(String message) {
        try {
            return objectMapper.readValue(message, ENVELOPE);
        } catch (JsonProcessingException e) {
            log.error("Malformed {} message skipped: {}", Topics.NOTIFY_DELIVERED, e.getClass().getSimpleName());
            return null;
        }
    }
}
