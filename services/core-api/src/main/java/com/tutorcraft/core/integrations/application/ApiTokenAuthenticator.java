package com.tutorcraft.core.integrations.application;

import com.tutorcraft.core.identity.UsersApi;
import com.tutorcraft.core.integrations.application.ApiTokenRepository.ActiveApiToken;
import com.tutorcraft.core.integrations.domain.ApiTokenFormat;
import com.tutorcraft.core.integrations.domain.ApiTokenScope;
import com.tutorcraft.core.shared.security.TokenHasher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Проверка personal access token для фильтра безопасности. Токен недействителен, если отозван, истёк
 * или его владелец не активен. last_used_at обновляется не чаще раза в минуту.
 */
@Component
public class ApiTokenAuthenticator {

    private static final String ACTIVE_STATUS = "active";
    private static final Duration TOUCH_INTERVAL = Duration.ofMinutes(1);

    private final ApiTokenRepository tokens;
    private final UsersApi users;
    private final Clock clock;

    public ApiTokenAuthenticator(ApiTokenRepository tokens, UsersApi users, Clock clock) {
        this.tokens = tokens;
        this.users = users;
        this.clock = clock;
    }

    public boolean supports(String bearerToken) {
        return ApiTokenFormat.matches(bearerToken);
    }

    public Optional<TokenPrincipal> authenticate(String rawToken) {
        if (!supports(rawToken)) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        Optional<ActiveApiToken> token = tokens.findActiveByHash(TokenHasher.sha256(rawToken), now)
                .filter(found -> ownerIsActive(found.tenantId(), found.userId()));
        token.filter(found -> needsTouch(found, now)).ifPresent(found -> tokens.touch(found.id(), now));
        return token.map(found -> new TokenPrincipal(found.id(), found.tenantId(), found.userId(), found.scopes()));
    }

    private boolean ownerIsActive(UUID tenantId, UUID userId) {
        return users.find(tenantId, userId).map(user -> ACTIVE_STATUS.equals(user.status())).orElse(false);
    }

    private static boolean needsTouch(ActiveApiToken token, Instant now) {
        return token.lastUsedAt() == null || token.lastUsedAt().plus(TOUCH_INTERVAL).isBefore(now);
    }

    public record TokenPrincipal(UUID tokenId, UUID tenantId, UUID userId, Set<ApiTokenScope> scopes) {

        public boolean permits(String httpMethod) {
            return ApiTokenScope.permits(scopes, httpMethod);
        }
    }
}
