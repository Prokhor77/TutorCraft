package com.tutorcraft.core.communication.notifications.application;

import com.tutorcraft.core.communication.NotificationCategory;
import com.tutorcraft.core.communication.notifications.domain.NotificationChannel;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Явные настройки уведомлений пользователей (отсутствие строки — умолчание категории). */
public interface PreferenceRepository {

    /** Настройки категории для группы получателей: userId → канал → включён. */
    Map<UUID, Map<NotificationChannel, Boolean>> forCategory(UUID tenantId, Collection<UUID> userIds,
                                                             NotificationCategory category);

    /** Все явные настройки пользователя: категория → канал → включён. */
    Map<NotificationCategory, Map<NotificationChannel, Boolean>> ofUser(UUID tenantId, UUID userId);

    void upsert(UUID tenantId, UUID userId, NotificationCategory category, NotificationChannel channel, boolean enabled,
                Instant now);
}
