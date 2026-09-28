package com.tutorcraft.core.identity.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutorcraft.core.identity.application.TelegramLinkService;
import com.tutorcraft.core.identity.application.TelegramLinkService.TelegramLinked;
import com.tutorcraft.core.shared.outbox.EventEnvelope;
import com.tutorcraft.core.shared.outbox.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Консьюмер tc.telegram.linked.v1 (notifier → core-api): сохраняет chat_id пользователя. */
@Component
class TelegramLinkedListener {

    private static final Logger log = LoggerFactory.getLogger(TelegramLinkedListener.class);
    private static final TypeReference<EventEnvelope<TelegramLinked>> ENVELOPE = new TypeReference<>() { };

    private final ObjectMapper objectMapper;
    private final TelegramLinkService telegramLinks;

    TelegramLinkedListener(ObjectMapper objectMapper, TelegramLinkService telegramLinks) {
        this.objectMapper = objectMapper;
        this.telegramLinks = telegramLinks;
    }

    @KafkaListener(topics = Topics.TELEGRAM_LINKED)
    void onMessage(String message) {
        EventEnvelope<TelegramLinked> envelope = parse(message);
        if (envelope == null || !supported(envelope) || envelope.eventId() == null || envelope.payload() == null) {
            return;
        }
        telegramLinks.completeLink(envelope.eventId(), envelope.payload());
    }

    private boolean supported(EventEnvelope<?> envelope) {
        if (envelope.supportedVersion()) {
            return true;
        }
        log.warn("{} event {} skipped: unsupported envelope version {}", Topics.TELEGRAM_LINKED, envelope.eventId(), envelope.version());
        return false;
    }

    /** Нечитаемое сообщение не может быть обработано повторно — логируем и пропускаем. */
    private EventEnvelope<TelegramLinked> parse(String message) {
        try {
            return objectMapper.readValue(message, ENVELOPE);
        } catch (JsonProcessingException e) {
            log.error("Malformed {} message skipped: {}", Topics.TELEGRAM_LINKED, e.getClass().getSimpleName());
            return null;
        }
    }
}
