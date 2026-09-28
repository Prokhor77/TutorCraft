package com.tutorcraft.core.communication.notifications.application;

import com.tutorcraft.core.communication.notifications.domain.NotificationChannel;
import com.tutorcraft.core.shared.api.PageQuery;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** In-app уведомления и журнал доставок во внешние каналы. */
public interface NotificationRepository {

    /** @return false — уведомление с таким dedupeKey у пользователя уже есть */
    boolean insertInApp(StoredNotification notification);

    /** @return false — доставка в канал по этому событию уже поставлена */
    boolean insertDelivery(UUID tenantId, UUID notificationId, UUID userId, String dedupeKey, NotificationChannel channel,
                           Instant now);

    List<StoredNotification> list(UUID tenantId, UUID userId, PageQuery page);

    long unreadCount(UUID tenantId, UUID userId);

    void markRead(UUID tenantId, UUID userId, Collection<UUID> ids, Instant now);

    void markAllRead(UUID tenantId, UUID userId, Instant now);

    void updateDelivery(UUID notificationId, String channel, String status, String error, Instant now);

    record StoredNotification(UUID id, UUID tenantId, UUID userId, String category, String title, String body, String link,
                              String dedupeKey, Instant readAt, Instant createdAt) {
    }
}
