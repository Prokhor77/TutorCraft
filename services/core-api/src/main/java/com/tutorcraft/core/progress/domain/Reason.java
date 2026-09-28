package com.tutorcraft.core.progress.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Невыполненное условие — машинное описание причины недоступности (UX-07). Текст формирует прикладной слой.
 * Поля, неприменимые к коду, — null.
 */
public record Reason(Code code, UUID itemId, UUID groupId, Instant at, Double min, Double max, Double current) {

    public enum Code {
        /** Ещё не наступила дата открытия. */
        DATE_FROM,
        /** Срок доступа истёк — условие больше не выполнится. */
        DATE_UNTIL,
        COMPLETION_COMPLETE,
        COMPLETION_INCOMPLETE,
        GRADE_MIN,
        GRADE_MAX,
        GRADE_RANGE,
        GROUP
    }

    public static Reason date(Code code, Instant at) {
        return new Reason(code, null, null, at, null, null, null);
    }

    public static Reason completion(Code code, UUID itemId) {
        return new Reason(code, itemId, null, null, null, null, null);
    }

    public static Reason grade(Code code, UUID itemId, Double min, Double max, Double current) {
        return new Reason(code, itemId, null, null, min, max, current);
    }

    public static Reason group(UUID groupId) {
        return new Reason(Code.GROUP, null, groupId, null, null, null, null);
    }
}
