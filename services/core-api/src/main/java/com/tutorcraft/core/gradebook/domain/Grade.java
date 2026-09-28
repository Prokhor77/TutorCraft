package com.tutorcraft.core.gradebook.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Оценка студента по столбцу журнала. rawScore — от источника (задание/тест), finalScore — действующая
 * (равна raw, если не переопределена вручную). Переопределённые и заблокированные оценки источник не меняет (FR-GRADE-04).
 */
public record Grade(UUID id, UUID tenantId, UUID columnId, UUID userId, BigDecimal rawScore, BigDecimal finalScore,
                    boolean overridden, boolean locked, Instant publishedAt, UUID gradedBy, Instant gradedAt,
                    long version) {

    public static Grade empty(UUID id, UUID tenantId, UUID columnId, UUID userId) {
        return new Grade(id, tenantId, columnId, userId, null, null, false, false, null, null, null, 0);
    }

    public boolean published() {
        return publishedAt != null;
    }

    public boolean acceptsSourceScore() {
        return !locked && !overridden;
    }

    /** Оценка источника; публикация не отменяется, а только выполняется при publish = true. */
    public Grade withSourceScore(BigDecimal score, UUID graderId, Instant now, boolean publish) {
        Instant published = publishedAt == null && publish ? now : publishedAt;
        return new Grade(id, tenantId, columnId, userId, score, score, false, locked, published, graderId, now, version);
    }

    /** Ручное изменение в журнале: оценка переопределена и далее не перезаписывается источником. */
    public Grade withOverride(BigDecimal score, boolean lock, UUID actorId, Instant now) {
        return new Grade(id, tenantId, columnId, userId, rawScore, score, true, lock, publishedAt, actorId, now, version);
    }

    /** Значение ручного столбца (у ручного столбца нет источника — переопределением не считается). */
    public Grade withManualScore(BigDecimal score, UUID actorId, Instant now) {
        Instant published = publishedAt == null ? now : publishedAt;
        return new Grade(id, tenantId, columnId, userId, score, score, false, locked, published, actorId, now, version);
    }

    public Grade publishedAt(Instant now) {
        return new Grade(id, tenantId, columnId, userId, rawScore, finalScore, overridden, locked, now, gradedBy, gradedAt,
                version);
    }

    public boolean scoreDiffers(Grade other) {
        return !sameScore(finalScore, other.finalScore);
    }

    private static boolean sameScore(BigDecimal first, BigDecimal second) {
        if (first == null || second == null) {
            return Objects.equals(first, second);
        }
        return first.compareTo(second) == 0;
    }
}
