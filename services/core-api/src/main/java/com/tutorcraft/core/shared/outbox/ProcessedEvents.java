package com.tutorcraft.core.shared.outbox;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Идемпотентность Kafka-консьюмеров core-api: событие обрабатывается ровно один раз. */
@Component
public class ProcessedEvents {

    private final JdbcClient jdbc;

    public ProcessedEvents(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** @return true, если событие отмечено впервые (его нужно обработать в текущей транзакции). */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean markProcessed(UUID eventId, String consumer) {
        int inserted = jdbc.sql("""
                INSERT INTO processed_events (event_id, consumer) VALUES (:eventId, :consumer)
                ON CONFLICT DO NOTHING
                """)
            .param("eventId", eventId).param("consumer", consumer).update();
        return inserted == 1;
    }
}
