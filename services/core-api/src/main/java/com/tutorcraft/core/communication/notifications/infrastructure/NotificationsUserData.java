package com.tutorcraft.core.communication.notifications.infrastructure;

import com.tutorcraft.core.identity.spi.UserDataEraser;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Удаление пользователя: уведомления, доставки и настройки уведомлений. */
@Component
class NotificationsUserData implements UserDataEraser {

    private static final String[] STATEMENTS = {
        "DELETE FROM notification_deliveries WHERE tenant_id = :tenantId AND user_id = :userId",
        "DELETE FROM notifications WHERE tenant_id = :tenantId AND user_id = :userId",
        "DELETE FROM notification_preferences WHERE tenant_id = :tenantId AND user_id = :userId",
    };

    private final JdbcClient jdbc;

    NotificationsUserData(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void eraseUser(UUID tenantId, UUID userId) {
        for (String statement : STATEMENTS) {
            jdbc.sql(statement).param("tenantId", tenantId).param("userId", userId).update();
        }
    }
}
