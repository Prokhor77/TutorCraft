package com.tutorcraft.core.identity.application;

import com.tutorcraft.core.audit.AuditLog;
import com.tutorcraft.core.audit.AuditRecord;
import com.tutorcraft.core.identity.application.AccountProvisioner.Signup;
import com.tutorcraft.core.identity.application.SessionService.LoginMethod;
import com.tutorcraft.core.identity.domain.EmailAddress;
import com.tutorcraft.core.identity.domain.RefreshTokenRecord;
import com.tutorcraft.core.identity.domain.UserAccount;
import com.tutorcraft.core.shared.domain.UnauthorizedException;
import com.tutorcraft.core.shared.domain.Validator;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import com.tutorcraft.core.shared.security.TokenHasher;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Регистрация репетитора, обновление и завершение сессий (FR-AUTH-04, ADR-003).
 * Вход по паролю — {@link PasswordLoginService}.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_SCHOOL_LENGTH = 200;

    private final AccountProvisioner provisioner;
    private final SessionService sessions;
    private final RefreshTokenRepository refreshTokens;
    private final UserRepository users;
    private final SignInGuard signInGuard;
    private final MeViewAssembler meAssembler;
    private final CurrentUserProvider currentUser;
    private final AuditLog audit;
    private final Clock clock;

    public AuthService(AccountProvisioner provisioner, SessionService sessions, RefreshTokenRepository refreshTokens,
                       UserRepository users, SignInGuard signInGuard, MeViewAssembler meAssembler,
                       CurrentUserProvider currentUser, AuditLog audit, Clock clock) {
        this.provisioner = provisioner;
        this.sessions = sessions;
        this.refreshTokens = refreshTokens;
        this.users = users;
        this.signInGuard = signInGuard;
        this.meAssembler = meAssembler;
        this.currentUser = currentUser;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public AuthResult register(RegisterCommand command) {
        validate(command);
        String email = EmailAddress.normalize(command.email());
        UserAccount owner = provisioner.createTenantOwner(new Signup(email, command.password(), command.firstName().trim(),
                command.lastName().trim(), command.schoolName(), null, null));
        return toResult(sessions.open(owner, LoginMethod.REGISTER));
    }

    /** Ротация refresh-токена. Повторное использование отозванного токена отзывает всё семейство. */
    public AuthResult refresh(String rawToken) {
        RefreshTokenRecord token = findToken(rawToken);
        switch (token.decide(clock.instant())) {
            case REUSE_DETECTED -> throw reuseDetected(token);
            case EXPIRED -> throw invalidRefresh();
            case ROTATE -> log.debug("Rotating refresh token of family {}", token.familyId());
        }
        UserAccount user = users.findById(token.tenantId(), token.userId()).orElseThrow(AuthService::invalidRefresh);
        signInGuard.ensureCanSignIn(user);
        try {
            return toResult(sessions.rotate(token, user));
        } catch (SessionService.RefreshReuseException e) {
            throw reuseDetected(token);
        }
    }

    /** Завершает текущую сессию (семейство refresh-токена из cookie). Отсутствующий или чужой токен игнорируется. */
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokens.findByHash(TokenHasher.sha256(rawToken)).ifPresent(token -> sessions.revokeFamily(token.familyId()));
    }

    @Transactional
    public void logoutAll() {
        CurrentUser user = currentUser.require();
        sessions.revokeAll(user.tenantId(), user.userId());
        audit.record(AuditRecord.of(user.tenantId(), user.userId(), "auth.sessions_revoked", "user", user.userId().toString()));
    }

    public AuthResult toResult(IssuedSession session) {
        return new AuthResult(session, meAssembler.assemble(session.user()));
    }

    private RefreshTokenRecord findToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalidRefresh();
        }
        return refreshTokens.findByHash(TokenHasher.sha256(rawToken)).orElseThrow(AuthService::invalidRefresh);
    }

    /** Отзыв семейства и аудит выполняются вне транзакции запроса, чтобы не откатиться вместе с ошибкой 401. */
    private UnauthorizedException reuseDetected(RefreshTokenRecord token) {
        sessions.revokeFamily(token.familyId());
        log.warn("Refresh token reuse detected: family {} of user {} revoked", token.familyId(), token.userId());
        audit.record(AuditRecord.of(token.tenantId(), token.userId(), "auth.refresh_reuse_detected", "user",
                token.userId().toString()));
        return invalidRefresh();
    }

    private static UnauthorizedException invalidRefresh() {
        return new UnauthorizedException(IdentityErrors.REFRESH_INVALID, "Refresh token is invalid");
    }

    private static void validate(RegisterCommand command) {
        new Validator()
            .check(EmailAddress.isValid(command.email()), "email", "invalid_email", "Invalid email")
            .notBlank(command.firstName(), "firstName")
            .maxLength(command.firstName(), MAX_NAME_LENGTH, "firstName")
            .notBlank(command.lastName(), "lastName")
            .maxLength(command.lastName(), MAX_NAME_LENGTH, "lastName")
            .maxLength(command.schoolName(), MAX_SCHOOL_LENGTH, "schoolName")
            .notBlank(command.password(), "password")
            .throwIfInvalid();
    }

    public record RegisterCommand(String email, String password, String firstName, String lastName, String schoolName) {

        @Override
        public String toString() {
            return "RegisterCommand[***]";
        }
    }
}
