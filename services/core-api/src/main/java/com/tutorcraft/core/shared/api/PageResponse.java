package com.tutorcraft.core.shared.api;

import java.util.List;
import java.util.function.Function;

/** Курсорная страница (API-04). */
public record PageResponse<T>(List<T> items, String nextCursor) {

    public <R> PageResponse<R> map(Function<T, R> mapper) {
        return new PageResponse<>(items.stream().map(mapper).toList(), nextCursor);
    }
}
