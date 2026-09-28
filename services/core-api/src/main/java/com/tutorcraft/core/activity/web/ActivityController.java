package com.tutorcraft.core.activity.web;

import com.tutorcraft.core.activity.application.ActivityFilter;
import com.tutorcraft.core.activity.application.ActivityQueryService;
import com.tutorcraft.core.activity.application.ActivityViews.EntryView;
import com.tutorcraft.core.activity.application.ActivityViews.SummaryView;
import com.tutorcraft.core.activity.application.ActivityViews.TrailView;
import com.tutorcraft.core.activity.application.ClientEventService;
import com.tutorcraft.core.activity.application.ClientEventService.ClientEvent;
import com.tutorcraft.core.activity.application.ClientEventService.ClientOrigin;
import com.tutorcraft.core.activity.domain.ActivityKind;
import com.tutorcraft.core.activity.domain.ActivityOutcome;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.api.PageResponse;
import com.tutorcraft.core.shared.domain.ValidationException;
import com.tutorcraft.core.shared.web.ClientIp;
import com.tutorcraft.core.shared.web.RequestCorrelation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ActivityController {

    static final int MAX_EVENTS_PER_BATCH = 20;

    private final ActivityQueryService queries;
    private final ClientEventService clientEvents;

    ActivityController(ActivityQueryService queries, ClientEventService clientEvents) {
        this.queries = queries;
        this.clientEvents = clientEvents;
    }

    @GetMapping("/api/v1/activity-log")
    PageResponse<EntryView> search(@RequestParam(required = false) UUID userId,
                                   @RequestParam(required = false) String actor,
                                   @RequestParam(required = false) String kind,
                                   @RequestParam(required = false) String outcome,
                                   @RequestParam(required = false) Integer status,
                                   @RequestParam(required = false) String route,
                                   @RequestParam(required = false) String requestId,
                                   @RequestParam(required = false) String sessionId,
                                   @RequestParam(required = false) Instant from,
                                   @RequestParam(required = false) Instant to,
                                   @RequestParam(defaultValue = "false") boolean includeAnonymous,
                                   @RequestParam(required = false) String cursor,
                                   @RequestParam(required = false) Integer limit) {
        ActivityFilter filter = new ActivityFilter(userId, actor, parse("kind", kind, ActivityKind::fromKey),
                parse("outcome", outcome, ActivityOutcome::fromKey), status, route, requestId, sessionId, from, to,
                includeAnonymous);
        return queries.search(filter, PageQuery.of(cursor, limit));
    }

    @GetMapping("/api/v1/activity-log/summary")
    SummaryView summary(@RequestParam(required = false) Instant from,
                        @RequestParam(required = false) Instant to,
                        @RequestParam(defaultValue = "false") boolean includeAnonymous) {
        return queries.summary(from, to, includeAnonymous);
    }

    @GetMapping("/api/v1/activity-log/{entryId}/trail")
    TrailView trail(@PathVariable UUID entryId) {
        return queries.trail(entryId);
    }

    /** События браузера (переходы и ошибки); пачкой, чтобы не делать запрос на каждый переход. */
    @PostMapping("/api/v1/activity/events")
    ResponseEntity<Void> ingest(@Valid @RequestBody ClientEventsRequest body, HttpServletRequest request) {
        ClientOrigin origin = new ClientOrigin(ClientIp.of(request),
                truncate(request.getHeader("User-Agent"), RequestActivityMapper.MAX_USER_AGENT_LENGTH),
                request.getHeader(RequestCorrelation.CLIENT_SESSION_HEADER));
        clientEvents.ingest(body.events().stream().map(ClientEventRequest::toEvent).toList(), origin);
        return ResponseEntity.noContent().build();
    }

    record ClientEventsRequest(@NotEmpty @Size(max = MAX_EVENTS_PER_BATCH) List<@Valid @NotNull ClientEventRequest> events) {
    }

    record ClientEventRequest(@NotBlank @Size(max = 32) String kind,
                              @Size(max = 300) String page,
                              @Size(max = 200) String name,
                              @Size(max = 2000) String message,
                              @Size(max = 8000) String stack,
                              @Size(max = 64) String requestId,
                              Instant occurredAt) {

        ClientEvent toEvent() {
            return new ClientEvent(kind, page, name, message, stack, requestId, occurredAt);
        }
    }

    private static <T> T parse(String field, String raw, Function<String, java.util.Optional<T>> parser) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return parser.apply(raw).orElseThrow(() -> ValidationException.single(field, "invalid", "Unknown " + field));
    }

    private static String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
