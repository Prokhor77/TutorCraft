package com.tutorcraft.core.activity.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.activity.application.ActivityViews.EntryView;
import com.tutorcraft.core.activity.application.ActivityViews.SummaryView;
import com.tutorcraft.core.activity.application.ActivityViews.TrailView;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.api.PageResponse;
import com.tutorcraft.core.shared.domain.ForbiddenException;
import com.tutorcraft.core.shared.domain.NotFoundException;
import com.tutorcraft.core.shared.domain.ValidationException;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Журнал активности для администратора (FR-REPORT-02): поиск, трассировка ошибки, сводка. */
@Service
public class ActivityQueryService {

    static final String ANCHOR_USER = "user";
    static final String ANCHOR_SESSION = "session";
    static final String ANCHOR_IP = "ip";
    static final String ANCHOR_NONE = "none";

    private static final Duration DEFAULT_SUMMARY_WINDOW = Duration.ofHours(24);
    private static final Duration MAX_SUMMARY_WINDOW = Duration.ofDays(31);
    private static final int TOP_ERROR_ROUTES = 5;
    private static final int MAX_TEXT_FILTER_LENGTH = 200;

    private final ActivityLogRepository repository;
    private final AccessService access;
    private final CurrentUserProvider currentUser;
    private final ActivityProperties properties;
    private final Clock clock;

    public ActivityQueryService(ActivityLogRepository repository, AccessService access, CurrentUserProvider currentUser,
                                ActivityProperties properties, Clock clock) {
        this.repository = repository;
        this.access = access;
        this.currentUser = currentUser;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<EntryView> search(ActivityFilter filter, PageQuery page) {
        validateText("actor", filter.actor());
        validateText("route", filter.route());
        ActivityScope scope = scope(filter.includeAnonymous());
        return page.toPage(repository.search(scope, filter, page), EntryView::at, EntryView::id);
    }

    /** Действия того же пользователя (или вкладки браузера, или IP для анонимных) вокруг записи. */
    @Transactional(readOnly = true)
    public TrailView trail(UUID entryId) {
        ActivityScope scope = scope(canSeeAnonymous());
        EntryView focus = repository.find(scope, entryId)
                .orElseThrow(() -> new NotFoundException("activity.not_found", "Activity entry not found"));
        Instant from = focus.at().minus(properties.trailBefore());
        Instant to = focus.at().plus(properties.trailAfter());
        String anchor = anchorOf(focus);
        if (ANCHOR_NONE.equals(anchor)) {
            return new TrailView(focus, anchor, from, to, List.of(focus), false);
        }
        TrailQuery query = new TrailQuery(from, to, focus.userId(), anchorSession(focus, anchor),
                ANCHOR_IP.equals(anchor) ? focus.ip() : null, properties.trailLimit());
        List<EntryView> events = repository.trail(scope, query);
        boolean truncated = events.size() > properties.trailLimit();
        List<EntryView> visible = truncated ? events.subList(0, properties.trailLimit()) : events;
        return new TrailView(focus, anchor, from, to, List.copyOf(visible), truncated);
    }

    @Transactional(readOnly = true)
    public SummaryView summary(Instant from, Instant to, boolean includeAnonymous) {
        ActivityScope scope = scope(includeAnonymous);
        Instant end = to == null ? clock.instant() : to;
        Instant start = from == null ? end.minus(DEFAULT_SUMMARY_WINDOW) : from;
        if (!start.isBefore(end) || Duration.between(start, end).compareTo(MAX_SUMMARY_WINDOW) > 0) {
            throw ValidationException.single("from", "out_of_range", "Summary window must be positive and at most 31 days");
        }
        return repository.summarize(scope, start, end, TOP_ERROR_ROUTES);
    }

    static String anchorOf(EntryView entry) {
        if (entry.userId() != null) {
            return ANCHOR_USER;
        }
        if (entry.sessionId() != null) {
            return ANCHOR_SESSION;
        }
        return entry.ip() != null ? ANCHOR_IP : ANCHOR_NONE;
    }

    private static String anchorSession(EntryView focus, String anchor) {
        return ANCHOR_SESSION.equals(anchor) ? focus.sessionId() : null;
    }

    private ActivityScope scope(boolean includeAnonymous) {
        access.require(Permission.AUDIT_VIEW, AccessContext.tenant());
        UUID tenantId = currentUser.require().tenantId();
        if (includeAnonymous && !canSeeAnonymous()) {
            throw new ForbiddenException("activity.anonymous_forbidden", "Only the platform administrator sees anonymous activity");
        }
        return new ActivityScope(tenantId, includeAnonymous);
    }

    /** Анонимные записи не принадлежат ни одной школе — их видит только главный администратор платформы. */
    private boolean canSeeAnonymous() {
        return access.can(Permission.PLATFORM_MANAGE, AccessContext.tenant());
    }

    private static void validateText(String field, String value) {
        if (value != null && value.length() > MAX_TEXT_FILTER_LENGTH) {
            throw ValidationException.single(field, "too_long", field + " must be at most " + MAX_TEXT_FILTER_LENGTH + " characters");
        }
    }
}
