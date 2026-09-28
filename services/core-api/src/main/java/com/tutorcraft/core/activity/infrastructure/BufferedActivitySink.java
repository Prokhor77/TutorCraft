package com.tutorcraft.core.activity.infrastructure;

import com.tutorcraft.core.activity.application.ActivityLogRepository;
import com.tutorcraft.core.activity.application.ActivityProperties;
import com.tutorcraft.core.activity.application.ActivitySink;
import com.tutorcraft.core.activity.domain.ActivityEntry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Буфер журнала активности: запрос только кладёт запись в ограниченную очередь (без ожидания), фоновая задача
 * пишет накопленное пачками. При переполнении очереди (БД недоступна, всплеск нагрузки) записи отбрасываются —
 * основной сценарий важнее журнала; потери видны в метрике {@value #DROPPED_METRIC} и в логе.
 */
@Component
class BufferedActivitySink implements ActivitySink {

    static final String DROPPED_METRIC = "tutorcraft.activity_log.dropped";
    static final String WRITTEN_METRIC = "tutorcraft.activity_log.written";

    private static final Logger log = LoggerFactory.getLogger(BufferedActivitySink.class);

    private final ActivityLogRepository repository;
    private final ActivityProperties properties;
    private final BlockingQueue<ActivityEntry> queue;
    private final Counter dropped;
    private final Counter written;
    private final AtomicLong droppedSinceLastFlush = new AtomicLong();

    BufferedActivitySink(ActivityLogRepository repository, ActivityProperties properties, MeterRegistry meters) {
        this.repository = repository;
        this.properties = properties;
        this.queue = new ArrayBlockingQueue<>(properties.queueCapacity());
        this.dropped = Counter.builder(DROPPED_METRIC).description("Activity log entries dropped").register(meters);
        this.written = Counter.builder(WRITTEN_METRIC).description("Activity log entries written").register(meters);
    }

    @Override
    public void submit(ActivityEntry entry) {
        if (!properties.enabled() || queue.offer(entry)) {
            return;
        }
        dropped.increment();
        droppedSinceLastFlush.incrementAndGet();
    }

    @Scheduled(fixedDelayString = "${tutorcraft.activity-log.flush-interval:PT1S}")
    public void flush() {
        reportDrops();
        List<ActivityEntry> batch = new ArrayList<>(properties.batchSize());
        while (queue.drainTo(batch, properties.batchSize()) > 0) {
            write(batch);
            batch.clear();
        }
    }

    @PreDestroy
    void flushOnShutdown() {
        flush();
    }

    private void write(List<ActivityEntry> batch) {
        try {
            repository.insertAll(batch);
            written.increment(batch.size());
        } catch (DataAccessException e) {
            dropped.increment(batch.size());
            log.error("Activity log batch of {} entries lost: {}", batch.size(), e.getClass().getSimpleName(), e);
        }
    }

    private void reportDrops() {
        long count = droppedSinceLastFlush.getAndSet(0);
        if (count > 0) {
            log.warn("Activity log queue overflow: {} entries dropped", count);
        }
    }
}
