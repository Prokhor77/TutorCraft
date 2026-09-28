package com.tutorcraft.core.integrations.infrastructure;

import com.tutorcraft.core.identity.spi.UserDataEraser;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Удаление пользователя: его персональные API-токены. */
@Component
class IntegrationsUserData implements UserDataEraser {

    private static final String[] STATEMENTS = {
        "DELETE FROM api_tokens WHERE tenant_id = :tenantId AND user_id = :userId",
    };

    private final JdbcClient jdbc;

    IntegrationsUserData(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void eraseUser(UUID tenantId, UUID userId) {
        for (String statement : STATEMENTS) {
            jdbc.sql(statement).param("tenantId", tenantId).param("userId", userId).update();
        }
    }
}
