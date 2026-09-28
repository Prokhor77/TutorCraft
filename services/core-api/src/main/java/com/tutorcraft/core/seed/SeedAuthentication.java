package com.tutorcraft.core.seed;

import com.tutorcraft.core.access.domain.TenantRole;
import com.tutorcraft.core.shared.security.JwtClaims;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Выполнение кода сида от имени пользователя: use case-сервисы берут текущего пользователя из SecurityContext
 * (CurrentUserProvider), как при запросе с access JWT. Контекст очищается после выполнения.
 */
@Component
class SeedAuthentication {

    private static final String TOKEN_VALUE = "demo-seed";
    private static final String ALGORITHM_HEADER = "alg";
    private static final String NO_SIGNATURE = "none";
    private static final Duration LIFETIME = Duration.ofMinutes(10);

    private final Clock clock;

    SeedAuthentication(Clock clock) {
        this.clock = clock;
    }

    void runAs(UUID tenantId, UUID userId, Runnable action) {
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new JwtAuthenticationToken(jwt(tenantId, userId), List.of(), userId.toString()));
        SecurityContextHolder.setContext(context);
        try {
            action.run();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    private Jwt jwt(UUID tenantId, UUID userId) {
        Instant now = clock.instant();
        return Jwt.withTokenValue(TOKEN_VALUE)
                .header(ALGORITHM_HEADER, NO_SIGNATURE)
                .issuer(JwtClaims.ISSUER)
                .subject(userId.toString())
                .claim(JwtClaims.TENANT_ID, tenantId.toString())
                .claim(JwtClaims.TENANT_ROLES, List.of(TenantRole.TENANT_ADMIN.key()))
                .claim(JwtClaims.TOKEN_TYPE, JwtClaims.ACCESS_TYPE)
                .issuedAt(now)
                .expiresAt(now.plus(LIFETIME))
                .build();
    }
}
