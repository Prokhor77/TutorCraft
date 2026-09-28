package com.tutorcraft.core.communication.notifications.application;

import com.tutorcraft.core.communication.NotificationCategory;
import com.tutorcraft.core.communication.notifications.application.NotificationRepository.StoredNotification;
import com.tutorcraft.core.communication.notifications.domain.NotificationChannel;
import com.tutorcraft.core.communication.notifications.domain.PreferenceDefaults;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.api.PageResponse;
import com.tutorcraft.core.shared.domain.FieldViolation;
import com.tutorcraft.core.shared.domain.ValidationException;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Центр уведомлений и настройки «категории × каналы» на одном экране (FR-NOTIF-01/02). */
@Service
public class NotificationCenterService {

    private static final int MAX_READ_IDS = 500;
    private static final String MATRIX_FIELD = "matrix";

    private final CurrentUserProvider currentUser;
    private final NotificationRepository notifications;
    private final PreferenceRepository preferences;
    private final Clock clock;

    NotificationCenterService(CurrentUserProvider currentUser, NotificationRepository notifications,
                              PreferenceRepository preferences, Clock clock) {
        this.currentUser = currentUser;
        this.notifications = notifications;
        this.preferences = preferences;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Inbox inbox(PageQuery page) {
        CurrentUser user = currentUser.require();
        List<StoredNotification> rows = notifications.list(user.tenantId(), user.userId(), page);
        PageResponse<NotificationView> result = page.toPage(rows, StoredNotification::createdAt, StoredNotification::id)
                .map(NotificationView::of);
        return new Inbox(result, notifications.unreadCount(user.tenantId(), user.userId()));
    }

    @Transactional
    public void markRead(List<UUID> ids, boolean all) {
        CurrentUser user = currentUser.require();
        Instant now = clock.instant();
        if (all) {
            notifications.markAllRead(user.tenantId(), user.userId(), now);
            return;
        }
        if (ids == null || ids.isEmpty() || ids.size() > MAX_READ_IDS) {
            throw ValidationException.single("ids", "invalid_count", "Pass 1.." + MAX_READ_IDS + " ids or all=true");
        }
        notifications.markRead(user.tenantId(), user.userId(), ids, now);
    }

    @Transactional(readOnly = true)
    public Map<String, Map<String, Boolean>> preferences() {
        CurrentUser user = currentUser.require();
        return matrix(preferences.ofUser(user.tenantId(), user.userId()));
    }

    /** Полная или частичная матрица; неизвестные категории/каналы — ошибка валидации. */
    @Transactional
    public Map<String, Map<String, Boolean>> updatePreferences(Map<String, Map<String, Boolean>> matrix) {
        CurrentUser user = currentUser.require();
        List<Change> changes = parse(matrix);
        Instant now = clock.instant();
        changes.forEach(change -> preferences.upsert(user.tenantId(), user.userId(), change.category(), change.channel(),
                change.enabled(), now));
        return matrix(preferences.ofUser(user.tenantId(), user.userId()));
    }

    private static Map<String, Map<String, Boolean>> matrix(Map<NotificationCategory, Map<NotificationChannel, Boolean>> saved) {
        Map<String, Map<String, Boolean>> matrix = new LinkedHashMap<>();
        Arrays.stream(NotificationCategory.values()).filter(PreferenceDefaults::configurable).forEach(category -> {
            Map<String, Boolean> row = new LinkedHashMap<>();
            Map<NotificationChannel, Boolean> explicit = saved.getOrDefault(category, Map.of());
            Arrays.stream(NotificationChannel.values()).forEach(channel -> row.put(channel.key(),
                    explicit.getOrDefault(channel, PreferenceDefaults.enabled(category, channel))));
            matrix.put(category.key(), row);
        });
        return matrix;
    }

    private static List<Change> parse(Map<String, Map<String, Boolean>> matrix) {
        if (matrix == null) {
            throw ValidationException.single(MATRIX_FIELD, "required", "matrix is required");
        }
        List<Change> changes = new ArrayList<>();
        List<FieldViolation> violations = new ArrayList<>();
        matrix.forEach((categoryKey, row) -> {
            Optional<NotificationCategory> category = NotificationCategory.fromKey(categoryKey)
                    .filter(PreferenceDefaults::configurable);
            if (category.isEmpty() || row == null) {
                violations.add(new FieldViolation(MATRIX_FIELD + "." + categoryKey, "invalid", "Unknown category"));
                return;
            }
            row.forEach((channelKey, enabled) -> NotificationChannel.find(channelKey).filter(channel -> enabled != null)
                    .ifPresentOrElse(channel -> changes.add(new Change(category.get(), channel, enabled)),
                            () -> violations.add(new FieldViolation(MATRIX_FIELD + "." + categoryKey + "." + channelKey,
                                    "invalid", "Unknown channel"))));
        });
        if (!violations.isEmpty()) {
            throw new ValidationException(violations);
        }
        return changes;
    }

    private record Change(NotificationCategory category, NotificationChannel channel, boolean enabled) {
    }

    public record Inbox(PageResponse<NotificationView> page, long unreadCount) {
    }

    /** Контракт Notification; type — категория. */
    public record NotificationView(UUID id, String type, String title, String body, String link, Instant readAt,
                                   Instant createdAt) {

        static NotificationView of(StoredNotification row) {
            return new NotificationView(row.id(), row.category(), row.title(), row.body(), row.link(), row.readAt(),
                    row.createdAt());
        }
    }
}
