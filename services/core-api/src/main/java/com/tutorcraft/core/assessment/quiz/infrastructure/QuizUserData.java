package com.tutorcraft.core.assessment.quiz.infrastructure;

import com.tutorcraft.core.identity.spi.UserDataEraser;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Удаление пользователя: попытки тестов с ответами и личные исключения; проверенные им чужие ответы теряют ссылку на проверяющего. */
@Component
class QuizUserData implements UserDataEraser {

    private static final String[] STATEMENTS = {
        "DELETE FROM attempt_answers WHERE tenant_id = :tenantId AND attempt_id IN (SELECT id FROM quiz_attempts WHERE tenant_id = :tenantId AND user_id = :userId)",
        "DELETE FROM quiz_attempts WHERE tenant_id = :tenantId AND user_id = :userId",
        "DELETE FROM quiz_overrides WHERE tenant_id = :tenantId AND user_id = :userId",
        "UPDATE attempt_answers SET graded_by = NULL WHERE tenant_id = :tenantId AND graded_by = :userId",
    };

    private final JdbcClient jdbc;

    QuizUserData(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void eraseUser(UUID tenantId, UUID userId) {
        for (String statement : STATEMENTS) {
            jdbc.sql(statement).param("tenantId", tenantId).param("userId", userId).update();
        }
    }
}
