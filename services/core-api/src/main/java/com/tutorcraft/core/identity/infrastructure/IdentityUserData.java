package com.tutorcraft.core.identity.infrastructure;

import com.tutorcraft.core.identity.spi.UserDataEraser;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Удаление пользователя: сессии, одноразовые токены, коды привязки Telegram и черновики импорта. */
@Component
class IdentityUserData implements UserDataEraser {

    private static final String[] STATEMENTS = {
        "DELETE FROM refresh_tokens WHERE tenant_id = :tenantId AND user_id = :userId",
        "DELETE FROM password_reset_tokens WHERE tenant_id = :tenantId AND user_id = :userId",
        "DELETE FROM invitations WHERE tenant_id = :tenantId AND user_id = :userId",
        "DELETE FROM telegram_link_codes WHERE tenant_id = :tenantId AND user_id = :userId",
        "DELETE FROM user_import_previews WHERE tenant_id = :tenantId AND created_by = :userId",
    };

    private final JdbcClient jdbc;

    IdentityUserData(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void eraseUser(UUID tenantId, UUID userId) {
        for (String statement : STATEMENTS) {
            jdbc.sql(statement).param("tenantId", tenantId).param("userId", userId).update();
        }
    }
}
