package com.tutorcraft.core.progress.infrastructure;

import com.tutorcraft.core.identity.spi.UserDataEraser;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Удаление пользователя: состояния выполнения, завершения курсов и отметки просмотров. */
@Component
class ProgressUserData implements UserDataEraser {

    private static final String[] STATEMENTS = {
        "DELETE FROM completion_states WHERE tenant_id = :tenantId AND user_id = :userId",
        "DELETE FROM course_completions WHERE tenant_id = :tenantId AND user_id = :userId",
        "DELETE FROM item_views WHERE tenant_id = :tenantId AND user_id = :userId",
    };

    private final JdbcClient jdbc;

    ProgressUserData(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void eraseUser(UUID tenantId, UUID userId) {
        for (String statement : STATEMENTS) {
            jdbc.sql(statement).param("tenantId", tenantId).param("userId", userId).update();
        }
    }
}
