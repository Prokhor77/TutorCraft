package com.tutorcraft.core.communication.forum.infrastructure;

import com.tutorcraft.core.identity.spi.UserDataEraser;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Удаление пользователя: текст его постов стирается, посты скрываются (ответы других остаются в ветке); подписки и отметки прочтения удаляются. */
@Component
class ForumUserData implements UserDataEraser {

    private static final String[] STATEMENTS = {
        "UPDATE forum_posts SET body = CAST('{\"schemaVersion\":1,\"blocks\":[]}' AS jsonb), hidden = true WHERE tenant_id = :tenantId AND author_id = :userId",
        "DELETE FROM forum_subscriptions WHERE tenant_id = :tenantId AND user_id = :userId",
        "DELETE FROM forum_reads WHERE tenant_id = :tenantId AND user_id = :userId",
    };

    private final JdbcClient jdbc;

    ForumUserData(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void eraseUser(UUID tenantId, UUID userId) {
        for (String statement : STATEMENTS) {
            jdbc.sql(statement).param("tenantId", tenantId).param("userId", userId).update();
        }
    }
}
