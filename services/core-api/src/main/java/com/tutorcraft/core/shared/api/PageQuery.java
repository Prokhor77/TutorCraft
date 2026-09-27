package com.tutorcraft.core.shared.api;

import com.tutorcraft.core.shared.domain.ValidationException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Параметры курсорной пагинации. Курсор — keyset (sortKey, id), закодирован {@link CursorCodec}.
 * Репозиторий запрашивает {@link #fetchSize()} строк и передаёт их в {@link #toPage}.
 */
public record PageQuery(Optional<CursorCodec.Position> after, int limit) {

    public static final int DEFAULT_LIMIT = 25;
    public static final int MAX_LIMIT = 100;

    public static PageQuery of(String cursor, Integer limit) {
        int effective = limit == null ? DEFAULT_LIMIT : limit;
        if (effective < 1 || effective > MAX_LIMIT) {
            throw ValidationException.single("limit", "out_of_range", "limit must be between 1 and " + MAX_LIMIT);
        }
        return new PageQuery(CursorCodec.decode(cursor), effective);
    }

    public int fetchSize() {
        return limit + 1;
    }

    public <T> PageResponse<T> toPage(List<T> fetched, Function<T, Instant> sortKey, Function<T, UUID> id) {
        if (fetched.size() <= limit) {
            return new PageResponse<>(fetched, null);
        }
        List<T> page = fetched.subList(0, limit);
        T last = page.get(page.size() - 1);
        return new PageResponse<>(List.copyOf(page), CursorCodec.encode(new CursorCodec.Position(sortKey.apply(last), id.apply(last))));
    }
}
