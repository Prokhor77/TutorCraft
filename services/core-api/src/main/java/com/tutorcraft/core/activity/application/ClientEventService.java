package com.tutorcraft.core.activity.application;

import com.tutorcraft.core.activity.domain.ActivityEntry;
import com.tutorcraft.core.activity.domain.ActivityKind;
import com.tutorcraft.core.activity.domain.ClientPage;
import com.tutorcraft.core.activity.domain.SensitiveDataMasker;
import com.tutorcraft.core.shared.domain.Ids;
import com.tutorcraft.core.shared.domain.ValidationException;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Приём событий веб-клиента: переходы по страницам и ошибки в браузере. Время события от клиента принимается,
 * только если оно в пределах последнего часа (буфер клиента), иначе ставится время приёма — подделать хронологию
 * журнала нельзя.
 */
@Service
public class ClientEventService {

    private static final Duration MAX_CLIENT_DELAY = Duration.ofHours(1);
    private static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(1);
    private static final int MAX_NAME_LENGTH = 200;
    private static final int MAX_MESSAGE_LENGTH = 1000;

    private final ActivitySink sink;
    private final CurrentUserProvider currentUser;
    private final ActivityProperties properties;
    private final Clock clock;

    public ClientEventService(ActivitySink sink, CurrentUserProvider currentUser, ActivityProperties properties,
                              Clock clock) {
        this.sink = sink;
        this.currentUser = currentUser;
        this.properties = properties;
        this.clock = clock;
    }

    /** Событие браузера. {@code name} — тип ошибки (TypeError, ChunkLoadError) или заголовок экрана. */
    public record ClientEvent(String kind, String page, String name, String message, String stack, String requestId,
                              Instant occurredAt) {
    }

    /** Откуда пришла пачка событий (заполняет контроллер из HTTP-запроса). */
    public record ClientOrigin(String ip, String userAgent, String sessionId) {
    }

    public void ingest(List<ClientEvent> events, ClientOrigin origin) {
        CurrentUser user = currentUser.require();
        if (!properties.enabled()) {
            return;
        }
        List<ActivityEntry> entries = events.stream().map(event -> toEntry(event, user, origin)).toList();
        entries.forEach(sink::submit);
    }

    private ActivityEntry toEntry(ClientEvent event, CurrentUser user, ClientOrigin origin) {
        ActivityKind kind = clientKind(event.kind());
        ActivityEntry.Actor actor = new ActivityEntry.Actor(user.tenantId(), user.userId(), origin.ip(), origin.userAgent());
        ActivityEntry.Correlation correlation = new ActivityEntry.Correlation(
                ClientPage.requestId(event.requestId()).orElse(null),
                ClientPage.sessionId(origin.sessionId()).orElse(null),
                ClientPage.page(event.page()).orElse(null));
        return new ActivityEntry(Ids.newId(), eventTime(event.occurredAt()), kind, actor, correlation, null,
                kind == ActivityKind.CLIENT_ERROR ? errorOf(event) : null);
    }

    private ActivityEntry.ErrorDetails errorOf(ClientEvent event) {
        return new ActivityEntry.ErrorDetails("client.error",
                SensitiveDataMasker.maskAndTruncate(event.name(), MAX_NAME_LENGTH),
                SensitiveDataMasker.maskAndTruncate(event.message(), MAX_MESSAGE_LENGTH),
                SensitiveDataMasker.maskAndTruncate(event.stack(), properties.maxStackLength()));
    }

    private static ActivityKind clientKind(String key) {
        return ActivityKind.fromKey(key)
                .filter(kind -> kind != ActivityKind.REQUEST)
                .orElseThrow(() -> ValidationException.single("kind", "invalid", "kind must be page_view or client_error"));
    }

    private Instant eventTime(Instant reported) {
        Instant now = clock.instant();
        if (reported == null || reported.isBefore(now.minus(MAX_CLIENT_DELAY)) || reported.isAfter(now.plus(MAX_CLOCK_SKEW))) {
            return now;
        }
        return reported;
    }
}
