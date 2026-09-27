package com.tutorcraft.core.shared.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutorcraft.core.shared.domain.Ids;
import com.tutorcraft.core.shared.persistence.Jsonb;
import java.time.Clock;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Запись события в outbox в той же транзакции, что и изменение данных (ARCH-04).
 * Публикация в Kafka — {@link OutboxRelay}.
 */
@Component
public class OutboxPublisher {

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxPublisher(JdbcClient jdbc, ObjectMapper objectMapper, Clock clock) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID publish(String topic, UUID tenantId, String type, Object payload) {
        UUID eventId = Ids.newId();
        EventEnvelope<Object> envelope = new EventEnvelope<>(eventId, type, EventEnvelope.CURRENT_VERSION,
                clock.instant(), tenantId, payload);
        jdbc.sql("""
                INSERT INTO outbox (id, topic, message_key, payload, created_at)
                VALUES (:id, :topic, :key, :payload, :createdAt)
                """)
            .param("id", eventId)
            .param("topic", topic)
            .param("key", tenantId.toString())
            .param("payload", Jsonb.of(serialize(envelope)))
            .param("createdAt", java.sql.Timestamp.from(envelope.occurredAt()))
            .update();
        return eventId;
    }

    private String serialize(EventEnvelope<Object> envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize event " + envelope.type(), e);
        }
    }
}
