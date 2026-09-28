package com.tutorcraft.core.identity.application;

import com.tutorcraft.core.audit.AuditLog;
import com.tutorcraft.core.audit.AuditRecord;
import com.tutorcraft.core.identity.application.SessionService.LoginMethod;
import com.tutorcraft.core.identity.domain.EmailAddress;
import com.tutorcraft.core.identity.domain.UserAccount;
import com.tutorcraft.core.org.OrgApi;
import com.tutorcraft.core.shared.domain.UnauthorizedException;
import com.tutorcraft.core.shared.security.PasswordHasher;
import com.tutorcraft.core.shared.security.TokenHasher;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Вход по email и паролю (FR-AUTH-01/03). Без tenantSlug ищутся учётные записи во всех tenant;
 * если пароль подошёл к нескольким — 409 {@code auth.tenant_required} со списком только этих tenant.
 * Метод не транзакционный: неудачные попытки фиксируются в аудите, а не откатываются вместе с ошибкой.
 */
@Service
public class PasswordLoginService {

    private static final Logger log = LoggerFactory.getLogger(PasswordLoginService.class);
    private static final String DUMMY_PASSWORD = "timing-equalizer-password";

    private final UserRepository users;
    private final OrgApi org;
    private final PasswordHasher hasher;
    private final LoginThrottle throttle;
    private final SessionService sessions;
    private final SignInGuard signInGuard;
    private final TenantChoice tenantChoice;
    private final AuthService auth;
    private final AuditLog audit;
    private final String dummyHash;

    public PasswordLoginService(UserRepository users, OrgApi org, PasswordHasher hasher, LoginThrottle throttle,
                                SessionService sessions, SignInGuard signInGuard, TenantChoice tenantChoice,
                                AuthService auth, AuditLog audit) {
        this.users = users;
        this.org = org;
        this.hasher = hasher;
        this.throttle = throttle;
        this.sessions = sessions;
        this.signInGuard = signInGuard;
        this.tenantChoice = tenantChoice;
        this.auth = auth;
        this.audit = audit;
        this.dummyHash = hasher.hash(DUMMY_PASSWORD);
    }

    public AuthResult login(LoginCommand command, String clientIp) {
        String email = EmailAddress.normalize(command.email());
        String accountKey = TokenHasher.sha256(email == null ? "" : email);
        throttle.retryAfter(accountKey, clientIp).ifPresent(wait -> {
            throw new LoginThrottledException(wait);
        });
        List<UserAccount> candidates = candidates(email, command.tenantSlug());
        List<UserAccount> matches = matchingPassword(candidates, command.password());
        if (matches.isEmpty()) {
            onFailure(accountKey, clientIp, candidates);
            throw invalidCredentials();
        }
        throttle.reset(accountKey);
        UserAccount user = tenantChoice.single(matches);
        signInGuard.ensureCanSignIn(user);
        return auth.toResult(sessions.open(user, LoginMethod.PASSWORD));
    }

    private List<UserAccount> candidates(String email, String tenantSlug) {
        if (email == null || !EmailAddress.isValid(email)) {
            return List.of();
        }
        if (tenantSlug == null || tenantSlug.isBlank()) {
            return users.findAllByEmail(email);
        }
        return org.findBySlug(tenantSlug.trim())
                .flatMap(tenant -> users.findByEmail(tenant.id(), email))
                .map(List::of)
                .orElse(List.of());
    }

    /** Хеш проверяется даже без кандидатов — время ответа не раскрывает существование аккаунта. */
    private List<UserAccount> matchingPassword(List<UserAccount> candidates, String password) {
        String raw = password == null ? "" : password;
        if (candidates.isEmpty()) {
            hasher.matches(raw, dummyHash);
            return List.of();
        }
        return candidates.stream().filter(user -> user.hasPassword() && hasher.matches(raw, user.passwordHash())).toList();
    }

    private void onFailure(String accountKey, String clientIp, List<UserAccount> candidates) {
        throttle.recordFailure(accountKey, clientIp);
        candidates.forEach(user -> audit.record(
                AuditRecord.of(user.tenantId(), user.id(), "auth.login_failed", "user", user.id().toString())));
        log.info("Password login failed ({} candidate accounts)", candidates.size());
    }

    private static UnauthorizedException invalidCredentials() {
        return new UnauthorizedException(IdentityErrors.INVALID_CREDENTIALS, "Invalid email or password");
    }

    public record LoginCommand(String email, String password, String tenantSlug) {

        @Override
        public String toString() {
            return "LoginCommand[tenantSlug=" + tenantSlug + "]";
        }
    }
}
