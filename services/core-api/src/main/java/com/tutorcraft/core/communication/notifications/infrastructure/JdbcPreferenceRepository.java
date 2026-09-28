package com.tutorcraft.core.communication.notifications.infrastructure;

import com.tutorcraft.core.communication.NotificationCategory;
import com.tutorcraft.core.communication.notifications.application.PreferenceRepository;
import com.tutorcraft.core.communication.notifications.domain.NotificationChannel;
import com.tutorcraft.core.shared.persistence.Timestamps;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcPreferenceRepository implements PreferenceRepository {

    private final JdbcClient jdbc;

    JdbcPreferenceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<UUID, Map<NotificationChannel, Boolean>> forCategory(UUID tenantId, Collection<UUID> userIds,
                                                                    NotificationCategory category) {
        Map<UUID, Map<NotificationChannel, Boolean>> result = new HashMap<>();
        if (userIds.isEmpty()) {
            return result;
        }
        jdbc.sql("""
                SELECT user_id, channel, enabled FROM notification_preferences
                WHERE tenant_id = :tenantId AND user_id IN (:userIds) AND category = :category
                """)
            .param("tenantId", tenantId).param("userIds", List.copyOf(userIds)).param("category", category.key())
            .query((rs, n) -> new Row(rs.getObject("user_id", UUID.class), null, rs.getString("channel"), rs.getBoolean("enabled")))
            .list()
            .forEach(row -> NotificationChannel.find(row.channel()).ifPresent(channel -> result
                    .computeIfAbsent(row.userId(), id -> new EnumMap<>(NotificationChannel.class)).put(channel, row.enabled())));
        return result;
    }

    @Override
    public Map<NotificationCategory, Map<NotificationChannel, Boolean>> ofUser(UUID tenantId, UUID userId) {
        Map<NotificationCategory, Map<NotificationChannel, Boolean>> result = new EnumMap<>(NotificationCategory.class);
        jdbc.sql("SELECT category, channel, enabled FROM notification_preferences WHERE tenant_id = :tenantId AND user_id = :userId")
            .param("tenantId", tenantId).param("userId", userId)
            .query((rs, n) -> new Row(userId, rs.getString("category"), rs.getString("channel"), rs.getBoolean("enabled")))
            .list()
            .forEach(row -> put(result, row));
        return result;
    }

    @Override
    public void upsert(UUID tenantId, UUID userId, NotificationCategory category, NotificationChannel channel, boolean enabled,
                       Instant now) {
        jdbc.sql("""
                INSERT INTO notification_preferences (tenant_id, user_id, category, channel, enabled, updated_at)
                VALUES (:tenantId, :userId, :category, :channel, :enabled, :now)
                ON CONFLICT (user_id, category, channel) DO UPDATE SET enabled = EXCLUDED.enabled, updated_at = EXCLUDED.updated_at
                """)
            .param("tenantId", tenantId).param("userId", userId).param("category", category.key())
            .param("channel", channel.key()).param("enabled", enabled).param("now", Timestamps.of(now))
            .update();
    }

    private static void put(Map<NotificationCategory, Map<NotificationChannel, Boolean>> result, Row row) {
        Optional<NotificationCategory> category = NotificationCategory.fromKey(row.category());
        Optional<NotificationChannel> channel = NotificationChannel.find(row.channel());
        if (category.isPresent() && channel.isPresent()) {
            result.computeIfAbsent(category.get(), key -> new EnumMap<>(NotificationChannel.class)).put(channel.get(), row.enabled());
        }
    }

    private record Row(UUID userId, String category, String channel, boolean enabled) {
    }
}
