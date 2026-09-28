package com.tutorcraft.core.activity.infrastructure;

import com.tutorcraft.core.activity.application.ActivityLogRepository;
import com.tutorcraft.core.activity.application.ActivityProperties;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Ежедневное удаление записей журнала активности старше {@code tutorcraft.activity-log.retention}. Удаляет
 * порциями, чтобы не держать длинную блокировку; повторный запуск безопасен.
 */
@Component
class ActivityRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(ActivityRetentionJob.class);
    private static final int PURGE_BATCH = 5_000;
    private static final int MAX_BATCHES_PER_RUN = 1_000;

    private final ActivityLogRepository repository;
    private final ActivityProperties properties;
    private final Clock clock;

    ActivityRetentionJob(ActivityLogRepository repository, ActivityProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${tutorcraft.activity-log.purge-cron:0 37 3 * * *}", zone = "UTC")
    public void purge() {
        Instant cutoff = clock.instant().minus(properties.retention());
        try {
            long removed = purgeBefore(cutoff);
            log.info("Activity log purge: {} entries older than {} removed", removed, cutoff);
        } catch (DataAccessException e) {
            log.error("Activity log purge failed, will retry on next run", e);
        }
    }

    private long purgeBefore(Instant cutoff) {
        long removed = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            int deleted = repository.purgeBefore(cutoff, PURGE_BATCH);
            removed += deleted;
            if (deleted < PURGE_BATCH) {
                break;
            }
        }
        return removed;
    }
}
