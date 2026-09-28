package com.tutorcraft.core.communication.calendar.infrastructure;

import com.tutorcraft.core.identity.spi.UserDataEraser;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Удаление пользователя: личные события календаря и iCal-токен. */
@Component
class CalendarUserData implements UserDataEraser {

    private static final String[] STATEMENTS = {
        "DELETE FROM calendar_personal_events WHERE tenant_id = :tenantId AND user_id = :userId",
        "DELETE FROM ical_tokens WHERE tenant_id = :tenantId AND user_id = :userId",
    };

    private final JdbcClient jdbc;

    CalendarUserData(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void eraseUser(UUID tenantId, UUID userId) {
        for (String statement : STATEMENTS) {
            jdbc.sql(statement).param("tenantId", tenantId).param("userId", userId).update();
        }
    }
}
