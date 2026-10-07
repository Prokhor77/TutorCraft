package com.tutorcraft.core.activity.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Модели чтения журнала активности (контракт API, docs/api/contract.md). */
public final class ActivityViews {

    private ActivityViews() {
    }

    /**
     * Запись журнала для администратора. {@code errorStack} заполнен только в трассировке; {@code tenantName} пуст
     * у анонимных записей и у записей удалённой школы.
     */
    public record EntryView(UUID id, Instant at, String kind, UUID tenantId, String tenantName, UUID userId, String actorName,
                            String actorEmail, String ip, String userAgent, String requestId, String sessionId,
                            String page, String method, String route, String path, Map<String, String> pathParams,
                            String handler, Integer status, Long durationMs, String errorCode, String errorType,
                            String errorMessage, String errorStack) {
    }

    /** Трассировка: сама запись и действия того же пользователя (сессии, IP) вокруг неё по времени. */
    public record TrailView(EntryView focus, String anchor, Instant from, Instant to, List<EntryView> events,
                            boolean truncated) {
    }

    public record RouteErrors(String method, String route, long count) {
    }

    public record SummaryView(Instant from, Instant to, long requests, long failedRequests, long serverErrors,
                              long clientErrors, long activeUsers, Long p95DurationMs, List<RouteErrors> topErrorRoutes) {
    }
}
