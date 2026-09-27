package com.tutorcraft.core.shared.outbox;

import com.tutorcraft.core.shared.config.AppProperties;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Переносит события из outbox в Kafka. Несколько экземпляров безопасны: FOR UPDATE SKIP LOCKED.
 * Доставка at-least-once; консьюмеры идемпотентны по eventId.
 */
@Component
class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final long SEND_TIMEOUT_SECONDS = 10;
    private static final int MAX_ERROR_LENGTH = 500;

    private final JdbcClient jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final Clock clock;
    private final int batchSize;

    OutboxRelay(JdbcClient jdbc, KafkaTemplate<String, String> kafka, Clock clock, AppProperties properties) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.clock = clock;
        this.batchSize = properties.kafka().outboxBatchSize();
    }

    @Scheduled(fixedDelayString = "${tutorcraft.kafka.outbox-poll-interval}")
    @Transactional
    public void relay() {
        List<OutboxRow> rows = jdbc.sql("""
                SELECT id, topic, message_key, payload::text AS payload FROM outbox
                WHERE published_at IS NULL
                ORDER BY created_at
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
                """)
            .param("limit", batchSize)
            .query((rs, n) -> new OutboxRow(rs.getObject("id", UUID.class), rs.getString("topic"),
                    rs.getString("message_key"), rs.getString("payload")))
            .list();
        rows.forEach(this::send);
    }

    private void send(OutboxRow row) {
        try {
            kafka.send(row.topic(), row.key(), row.payload()).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            jdbc.sql("UPDATE outbox SET published_at = :now WHERE id = :id")
                .param("now", java.sql.Timestamp.from(clock.instant())).param("id", row.id()).update();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            markFailed(row, e);
        } catch (ExecutionException | TimeoutException e) {
            markFailed(row, e);
        }
    }

    private void markFailed(OutboxRow row, Exception error) {
        log.warn("Outbox publish failed for event {} to {}: {}", row.id(), row.topic(), error.getClass().getSimpleName());
        String message = String.valueOf(error.getMessage());
        jdbc.sql("UPDATE outbox SET attempts = attempts + 1, last_error = :error WHERE id = :id")
            .param("error", message.substring(0, Math.min(message.length(), MAX_ERROR_LENGTH)))
            .param("id", row.id())
            .update();
    }

    private record OutboxRow(UUID id, String topic, String key, String payload) {
    }
}
