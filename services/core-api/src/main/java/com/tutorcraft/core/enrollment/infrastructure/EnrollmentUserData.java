package com.tutorcraft.core.enrollment.infrastructure;

import com.tutorcraft.core.identity.spi.UserDataEraser;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Удаление пользователя: членство в группах и записи на курсы. Созданные им ссылки-приглашения остаются. */
@Component
class EnrollmentUserData implements UserDataEraser {

    private static final String[] STATEMENTS = {
        "DELETE FROM course_group_members WHERE tenant_id = :tenantId AND user_id = :userId",
        "DELETE FROM enrollments WHERE tenant_id = :tenantId AND user_id = :userId",
    };

    private final JdbcClient jdbc;

    EnrollmentUserData(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void eraseUser(UUID tenantId, UUID userId) {
        for (String statement : STATEMENTS) {
            jdbc.sql(statement).param("tenantId", tenantId).param("userId", userId).update();
        }
    }
}
