package com.tutorcraft.core.courses.infrastructure;

import com.tutorcraft.core.courses.application.TrashService;
import com.mongodb.MongoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Ежедневная очистка корзины курсов (FR-COURSE-07). Повторный запуск безопасен (идемпотентно). */
@Component
class TrashPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(TrashPurgeJob.class);

    private final TrashService trash;

    TrashPurgeJob(TrashService trash) {
        this.trash = trash;
    }

    @Scheduled(cron = "${tutorcraft.courses.trash-purge-cron:0 17 3 * * *}", zone = "UTC")
    public void purge() {
        try {
            trash.purgeExpired();
        } catch (DataAccessException | MongoException e) {
            log.error("Trash purge failed, will retry on next run", e);
        }
    }
}
