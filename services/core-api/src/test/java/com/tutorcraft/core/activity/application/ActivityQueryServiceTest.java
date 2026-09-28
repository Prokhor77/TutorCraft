package com.tutorcraft.core.activity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.activity.application.ActivityViews.EntryView;
import com.tutorcraft.core.activity.application.ActivityViews.TrailView;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.domain.ForbiddenException;
import com.tutorcraft.core.shared.domain.ValidationException;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ActivityQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final int TRAIL_LIMIT = 2;

    private final ActivityLogRepository repository = mock(ActivityLogRepository.class);
    private final AccessService access = mock(AccessService.class);
    private final CurrentUserProvider currentUser = mock(CurrentUserProvider.class);
    private final ActivityProperties properties = new ActivityProperties(true, true, Duration.ofDays(30), 1000, 100,
            8000, Duration.ofMinutes(30), Duration.ofMinutes(5), TRAIL_LIMIT);
    private ActivityQueryService service;

    @BeforeEach
    void setUp() {
        when(currentUser.find()).thenReturn(Optional.of(new CurrentUser(USER, TENANT, Set.of())));
        when(currentUser.require()).thenCallRealMethod();
        service = new ActivityQueryService(repository, access, currentUser, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void searchIsScopedToCurrentTenant() {
        when(repository.search(any(), any(), any())).thenReturn(List.of());

        service.search(filter(false), PageQuery.of(null, null));

        verify(access).require(Permission.AUDIT_VIEW, AccessContext.tenant());
        verify(repository).search(eq(new ActivityScope(TENANT, false)), any(), any());
    }

    @Test
    void anonymousActivityRequiresPlatformAdmin() {
        when(access.can(Permission.PLATFORM_MANAGE, AccessContext.tenant())).thenReturn(false);

        assertThatThrownBy(() -> service.search(filter(true), PageQuery.of(null, null)))
                .isInstanceOf(ForbiddenException.class);
        verify(repository, never()).search(any(), any(), any());
    }

    @Test
    void trailFollowsUserAroundTheError() {
        EntryView focus = entry(USER, "tab-12345678", NOW);
        when(repository.find(any(), eq(focus.id()))).thenReturn(Optional.of(focus));
        when(repository.trail(any(), any())).thenReturn(List.of(focus, focus, focus));

        TrailView trail = service.trail(focus.id());

        ArgumentCaptor<TrailQuery> query = ArgumentCaptor.forClass(TrailQuery.class);
        verify(repository).trail(any(), query.capture());
        assertThat(query.getValue().userId()).isEqualTo(USER);
        assertThat(query.getValue().sessionId()).as("user anchor must not widen the trail to the tab").isNull();
        assertThat(query.getValue().ip()).isNull();
        assertThat(query.getValue().from()).isEqualTo(NOW.minus(Duration.ofMinutes(30)));
        assertThat(trail.anchor()).isEqualTo("user");
        assertThat(trail.truncated()).isTrue();
        assertThat(trail.events()).hasSize(TRAIL_LIMIT);
    }

    @Test
    void anonymousEntryWithoutSessionFallsBackToIp() {
        assertThat(ActivityQueryService.anchorOf(entry(null, null, NOW))).isEqualTo("ip");
        assertThat(ActivityQueryService.anchorOf(entry(null, "tab-12345678", NOW))).isEqualTo("session");
    }

    @Test
    void summaryWindowIsLimited() {
        assertThatThrownBy(() -> service.summary(NOW.minus(Duration.ofDays(40)), NOW, false))
                .isInstanceOf(ValidationException.class);
    }

    private static ActivityFilter filter(boolean includeAnonymous) {
        return new ActivityFilter(null, null, null, null, null, null, null, null, null, null, includeAnonymous);
    }

    private static EntryView entry(UUID userId, String sessionId, Instant at) {
        return new EntryView(UUID.randomUUID(), at, "request", TENANT, userId, null, null, "10.0.0.1", null, "r-1",
                sessionId, "/home", "GET", "/api/v1/me", "/api/v1/me", null, null, 500, 12L, "internal.error",
                null, null, null);
    }
}
