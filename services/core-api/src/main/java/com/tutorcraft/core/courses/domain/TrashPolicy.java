package com.tutorcraft.core.courses.domain;

import java.time.Duration;
import java.time.Instant;

/** Корзина (FR-COURSE-07, UX-06): удалённое восстанавливается в течение срока хранения, затем очищается. */
public record TrashPolicy(Duration retention) {

    public Instant purgeAt(Instant deletedAt) {
        return deletedAt.plus(retention);
    }

    /** Граница: удалённое раньше неё — просрочено. */
    public Instant cutoff(Instant now) {
        return now.minus(retention);
    }

    public boolean restorable(Instant deletedAt, Instant now) {
        return deletedAt != null && now.isBefore(purgeAt(deletedAt));
    }
}
