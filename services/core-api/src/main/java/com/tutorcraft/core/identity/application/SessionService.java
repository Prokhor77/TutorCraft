package com.tutorcraft.core.identity.application;

import com.tutorcraft.core.audit.AuditLog;
import com.tutorcraft.core.audit.AuditRecord;
import com.tutorcraft.core.identity.application.RefreshTokenRepository.NewRefreshToken;
import com.tutorcraft.core.identity.domain.RefreshTokenRecord;
import com.tutorcraft.core.identity.domain.UserAccount;
import com.tutorcraft.core.shared.config.AppProperties;
import com.tutorcraft.core.shared.domain.Ids;
import com.tutorcraft.core.shared.security.JwtService;
import com.tutorcraft.core.shared.security.TokenHasher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Выпуск, ротация и отзыв сессий (FR-AUTH-04, ADR-003). Семейство refresh-токенов = один вход;
 * повторное предъявление отозванного токена обрабатывает вызывающий код (отзыв семейства).
 */
@Service
public class SessionService {

    private static final String LOGIN_ACTION = "auth.login_succeeded";
    private static final String METHOD_KEY = "method";

    private final RefreshTokenRepository tokens;
    private final UserRepository users;
    private final JwtService jwt;
    private final MeViewAssembler meAssembler;
    private final AuditLog audit;
    private final Clock clock;
    private final Duration refreshTtl;

    public SessionService(RefreshTokenRepository tokens, UserRepository users, JwtService jwt, MeViewAssembler meAssembler,
                          AuditLog audit, Clock clock, AppProperties properties) {
        this.tokens = tokens;
        this.users = users;
        this.jwt = jwt;
        this.meAssembler = meAssembler;
        this.audit = audit;
        this.clock = clock;
        this.refreshTtl = properties.security().refreshTokenTtl();
    }

    /** Новый вход: новое семейство refresh-токенов, last_login_at, запись аудита. */
    @Transactional
    public IssuedSession open(UserAccount user, LoginMethod method) {
        users.recordLogin(user.tenantId(), user.id(), clock.instant());
        audit.record(AuditRecord.of(user.tenantId(), user.id(), LOGIN_ACTION, "user", user.id().toString())
                .withDiff(Map.of(METHOD_KEY, method.key())));
        return issue(user, Ids.newId(), Ids.newId());
    }

    /** Ротация: текущий токен отзывается и заменяется новым в том же семействе. */
    @Transactional
    public IssuedSession rotate(RefreshTokenRecord current, UserAccount user) {
        UUID nextId = Ids.newId();
        if (!tokens.revokeIfActive(current.id(), clock.instant(), nextId)) {
            throw new RefreshReuseException();
        }
        return issue(user, current.familyId(), nextId);
    }

    @Transactional
    public void revokeAll(UUID tenantId, UUID userId) {
        tokens.revokeAllOfUser(tenantId, userId, clock.instant());
    }

    @Transactional
    public void revokeFamily(UUID familyId) {
        tokens.revokeFamily(familyId, clock.instant());
    }

    private IssuedSession issue(UserAccount user, UUID familyId, UUID tokenId) {
        Instant now = clock.instant();
        String refreshToken = TokenHasher.newToken();
        tokens.insert(new NewRefreshToken(tokenId, user.tenantId(), user.id(), familyId, TokenHasher.sha256(refreshToken),
                now, now.plus(refreshTtl)));
        JwtService.IssuedToken access = jwt.issueAccessToken(user.id(), user.tenantId(), meAssembler.tenantRoleKeys(user));
        return new IssuedSession(access.value(), access.expiresInSeconds(), refreshToken, refreshTtl, user);
    }

    /** Способ входа — для журнала аудита. */
    public enum LoginMethod {
        PASSWORD("password"), REGISTER("register"), GOOGLE("google"), TELEGRAM("telegram"), INVITATION("invitation"),
        PASSWORD_CHANGE("password_change");

        private final String key;

        LoginMethod(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    /** Токен уже отозван к моменту ротации (параллельный запрос или кража). */
    static class RefreshReuseException extends RuntimeException {

        RefreshReuseException() {
            super("Refresh token already rotated");
        }
    }
}
