package com.tutorcraft.core.communication.notifications.infrastructure;

import com.tutorcraft.core.communication.notifications.application.NotificationRepository;
import com.tutorcraft.core.communication.notifications.domain.NotificationChannel;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.domain.Ids;
import com.tutorcraft.core.shared.persistence.Timestamps;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcNotificationRepository implements NotificationRepository {

    private static final String FIELDS = "id, tenant_id, user_id, category, title, body, link, dedupe_key, read_at, created_at";
    private static final int MAX_ERROR_LENGTH = 500;

    private final JdbcClient jdbc;

    JdbcNotificationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean insertInApp(StoredNotification n) {
        return jdbc.sql("""
                INSERT INTO notifications (id, tenant_id, user_id, category, title, body, link, dedupe_key, created_at)
                VALUES (:id, :tenantId, :userId, :category, :title, :body, :link, :dedupeKey, :createdAt)
                ON CONFLICT (user_id, dedupe_key) DO NOTHING
                """)
            .param("id", n.id()).param("tenantId", n.tenantId()).param("userId", n.userId()).param("category", n.category())
            .param("title", n.title()).param("body", n.body()).param("link", n.link()).param("dedupeKey", n.dedupeKey())
            .param("createdAt", Timestamps.of(n.createdAt()))
            .update() == 1;
    }

    @Override
    public boolean insertDelivery(UUID tenantId, UUID notificationId, UUID userId, String dedupeKey,
                                  NotificationChannel channel, Instant now) {
        return jdbc.sql("""
                INSERT INTO notification_deliveries (id, tenant_id, notification_id, user_id, dedupe_key, channel, status,
                                                     created_at, updated_at)
                VALUES (:id, :tenantId, :notificationId, :userId, :dedupeKey, :channel, 'pending', :now, :now)
                ON CONFLICT (user_id, dedupe_key, channel) DO NOTHING
                """)
            .param("id", Ids.newId()).param("tenantId", tenantId).param("notificationId", notificationId)
            .param("userId", userId).param("dedupeKey", dedupeKey).param("channel", channel.key())
            .param("now", Timestamps.of(now))
            .update() == 1;
    }

    @Override
    public List<StoredNotification> list(UUID tenantId, UUID userId, PageQuery page) {
        StringBuilder sql = new StringBuilder("SELECT " + FIELDS + " FROM notifications WHERE tenant_id = :tenantId AND user_id = :userId");
        Map<String, Object> params = new HashMap<>(Map.of("tenantId", tenantId, "userId", userId, "limit", page.fetchSize()));
        page.after().ifPresent(after -> {
            sql.append(" AND (created_at, id) < (:afterAt, :afterId)");
            params.put("afterAt", Timestamps.of(after.sortKey()));
            params.put("afterId", after.id());
        });
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT :limit");
        return jdbc.sql(sql.toString()).params(params).query((rs, n) -> toNotification(rs)).list();
    }

    @Override
    public long unreadCount(UUID tenantId, UUID userId) {
        return jdbc.sql("SELECT COUNT(*) FROM notifications WHERE tenant_id = :tenantId AND user_id = :userId AND read_at IS NULL")
                .param("tenantId", tenantId).param("userId", userId)
                .query(Long.class).single();
    }

    @Override
    public void markRead(UUID tenantId, UUID userId, Collection<UUID> ids, Instant now) {
        if (ids.isEmpty()) {
            return;
        }
        jdbc.sql("""
                UPDATE notifications SET read_at = :now
                WHERE tenant_id = :tenantId AND user_id = :userId AND id IN (:ids) AND read_at IS NULL
                """)
            .param("now", Timestamps.of(now)).param("tenantId", tenantId).param("userId", userId)
            .param("ids", List.copyOf(ids))
            .update();
    }

    @Override
    public void markAllRead(UUID tenantId, UUID userId, Instant now) {
        jdbc.sql("UPDATE notifications SET read_at = :now WHERE tenant_id = :tenantId AND user_id = :userId AND read_at IS NULL")
            .param("now", Timestamps.of(now)).param("tenantId", tenantId).param("userId", userId)
            .update();
    }

    @Override
    public void updateDelivery(UUID notificationId, String channel, String status, String error, Instant now) {
        jdbc.sql("""
                UPDATE notification_deliveries SET status = :status, error = :error, updated_at = :now
                WHERE notification_id = :notificationId AND channel = :channel
                """)
            .param("status", status).param("error", truncate(error)).param("now", Timestamps.of(now))
            .param("notificationId", notificationId).param("channel", channel)
            .update();
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() > MAX_ERROR_LENGTH ? error.substring(0, MAX_ERROR_LENGTH) : error;
    }

    private static StoredNotification toNotification(ResultSet rs) throws SQLException {
        return new StoredNotification(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getObject("user_id", UUID.class), rs.getString("category"), rs.getString("title"), rs.getString("body"),
                rs.getString("link"), rs.getString("dedupe_key"), Timestamps.read(rs, "read_at"),
                Timestamps.read(rs, "created_at"));
    }
}
