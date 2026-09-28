package com.tutorcraft.core.gradebook.infrastructure;

import com.tutorcraft.core.identity.spi.UserDataEraser;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Удаление пользователя: его оценки и их история; выставленные им чужие оценки теряют ссылку на проверяющего.
 * grade_history append-only (DATA-03): удаление разрешается только внутри транзакции флагом
 * {@code tutorcraft.retention_purge} (тот же механизм, что у очистки по сроку хранения), который сразу снимается.
 */
@Component
class GradebookUserData implements UserDataEraser {

    private static final String PURGE_FLAG = "tutorcraft.retention_purge";
    private static final String USER_GRADES = "SELECT id FROM grades WHERE tenant_id = :tenantId AND user_id = :userId";

    private final JdbcClient jdbc;

    GradebookUserData(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void eraseUser(UUID tenantId, UUID userId) {
        setPurgeFlag("on");
        try {
            run("DELETE FROM grade_history WHERE tenant_id = :tenantId AND grade_id IN (" + USER_GRADES + ")", tenantId, userId);
        } finally {
            setPurgeFlag("off");
        }
        run("DELETE FROM grades WHERE tenant_id = :tenantId AND user_id = :userId", tenantId, userId);
        run("UPDATE grades SET graded_by = NULL WHERE tenant_id = :tenantId AND graded_by = :userId", tenantId, userId);
    }

    private void setPurgeFlag(String value) {
        jdbc.sql("SELECT set_config(:name, :value, true)").param("name", PURGE_FLAG).param("value", value)
                .query(String.class).single();
    }

    private void run(String statement, UUID tenantId, UUID userId) {
        jdbc.sql(statement).param("tenantId", tenantId).param("userId", userId).update();
    }
}
