package com.tutorcraft.core.activity.infrastructure;

import com.tutorcraft.core.identity.spi.UserDataEraser;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Удаление пользователя: журнал его действий (IP, user-agent, страницы). */
@Component
class ActivityUserData implements UserDataEraser {

    private static final String[] STATEMENTS = {
        "DELETE FROM activity_log WHERE user_id = :userId",
    };

    private final JdbcClient jdbc;

    ActivityUserData(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void eraseUser(UUID tenantId, UUID userId) {
        for (String statement : STATEMENTS) {
            jdbc.sql(statement).param("tenantId", tenantId).param("userId", userId).update();
        }
    }
}
