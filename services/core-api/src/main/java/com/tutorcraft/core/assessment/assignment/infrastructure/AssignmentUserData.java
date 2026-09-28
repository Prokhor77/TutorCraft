package com.tutorcraft.core.assessment.assignment.infrastructure;

import com.tutorcraft.core.identity.spi.UserDataEraser;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Удаление пользователя: индивидуальные сдачи с файлами и отзывами, членство в групповых сдачах, личные продления сроков. Групповые сдачи и отзывы, которые он оставил другим, остаются. */
@Component
class AssignmentUserData implements UserDataEraser {

    private static final String[] STATEMENTS = {
        "DELETE FROM submission_files WHERE tenant_id = :tenantId AND submission_id IN (SELECT id FROM submissions WHERE tenant_id = :tenantId AND owner_key = 'u:' || CAST(:userId AS text))",
        "DELETE FROM submission_feedback WHERE tenant_id = :tenantId AND submission_id IN (SELECT id FROM submissions WHERE tenant_id = :tenantId AND owner_key = 'u:' || CAST(:userId AS text))",
        "DELETE FROM submission_members WHERE tenant_id = :tenantId AND user_id = :userId",
        "DELETE FROM submission_members WHERE tenant_id = :tenantId AND submission_id IN (SELECT id FROM submissions WHERE tenant_id = :tenantId AND owner_key = 'u:' || CAST(:userId AS text))",
        "DELETE FROM submissions WHERE tenant_id = :tenantId AND owner_key = 'u:' || CAST(:userId AS text)",
        "DELETE FROM item_overrides WHERE tenant_id = :tenantId AND user_id = :userId",
    };

    private final JdbcClient jdbc;

    AssignmentUserData(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void eraseUser(UUID tenantId, UUID userId) {
        for (String statement : STATEMENTS) {
            jdbc.sql(statement).param("tenantId", tenantId).param("userId", userId).update();
        }
    }
}
