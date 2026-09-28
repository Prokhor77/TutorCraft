package com.tutorcraft.core.activity.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Запись журнала активности: кто, что, где, чем закончилось. Не содержит тел запросов, query-строк, паролей и
 * токенов (NFR-SEC-11): только идентификаторы, шаблон маршрута и замаскированные сведения об ошибке.
 */
public record ActivityEntry(UUID id, Instant at, ActivityKind kind, Actor actor, Correlation correlation,
                            HttpDetails http, ErrorDetails error) {

    public ActivityEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(correlation, "correlation");
    }

    /** Кто: пользователь (null — анонимный запрос) и откуда. */
    public record Actor(UUID tenantId, UUID userId, String ip, String userAgent) {
    }

    /**
     * Связка событий: {@code requestId} — один HTTP-запрос, {@code sessionId} — вкладка браузера,
     * {@code page} — страница интерфейса, на которой произошло действие.
     */
    public record Correlation(String requestId, String sessionId, String page) {
    }

    /** Что: метод, шаблон маршрута, параметры пути (секреты замаскированы), обработчик, статус и длительность. */
    public record HttpDetails(String method, String route, String path, Map<String, String> pathParams,
                              String handler, int status, long durationMs) {
    }

    /** Чем закончилось: код ошибки; тип, сообщение и стек — только для непредвиденных ошибок. */
    public record ErrorDetails(String code, String type, String message, String stack) {
    }
}
