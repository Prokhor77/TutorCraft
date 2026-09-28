package com.tutorcraft.core.courses.domain;

/**
 * Поле частичного обновления (PATCH): отличает «поле не передано» от «передано null» (очистка значения).
 */
public final class Patch<T> {

    private static final Patch<?> ABSENT = new Patch<>(false, null);

    private final boolean present;
    private final T value;

    private Patch(boolean present, T value) {
        this.present = present;
        this.value = value;
    }

    @SuppressWarnings("unchecked")
    public static <T> Patch<T> absent() {
        return (Patch<T>) ABSENT;
    }

    public static <T> Patch<T> of(T value) {
        return new Patch<>(true, value);
    }

    public boolean isPresent() {
        return present;
    }

    public T value() {
        return value;
    }

    /** Новое значение, если поле передано, иначе текущее. */
    public T applyTo(T current) {
        return present ? value : current;
    }
}
