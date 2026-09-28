package com.tutorcraft.core.identity.application;

import com.tutorcraft.core.audit.AuditLog;
import com.tutorcraft.core.audit.AuditRecord;
import com.tutorcraft.core.communication.NotificationCategory;
import com.tutorcraft.core.communication.NotificationsApi;
import com.tutorcraft.core.communication.NotificationsApi.NotificationCommand;
import com.tutorcraft.core.identity.application.OneTimeTokenRepository.Kind;
import com.tutorcraft.core.identity.application.OneTimeTokenRepository.OneTimeToken;
import com.tutorcraft.core.identity.application.OneTimeTokens.IssuedToken;
import com.tutorcraft.core.identity.application.SessionService.LoginMethod;
import com.tutorcraft.core.identity.domain.EmailAddress;
import com.tutorcraft.core.identity.domain.UserAccount;
import com.tutorcraft.core.org.OrgApi;
import com.tutorcraft.core.shared.config.AppProperties;
import com.tutorcraft.core.shared.domain.BusinessRuleException;
import com.tutorcraft.core.shared.domain.NotFoundException;
import com.tutorcraft.core.shared.domain.ValidationException;
import com.tutorcraft.core.shared.i18n.Messages;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import com.tutorcraft.core.shared.security.PasswordHasher;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Сброс (FR-AUTH-02) и смена пароля. Любая смена пароля отзывает все сессии пользователя. */
@Service
public class PasswordService {

    private static final String RESET_PATH = "/reset-password?token=";
    private static final String RESET_MESSAGE = "identity.password_reset";
    private static final String NEW_PASSWORD_FIELD = "newPassword";
    private static final String CURRENT_PASSWORD_FIELD = "currentPassword";

    private final UserRepository users;
    private final OrgApi org;
    private final OneTimeTokens tokens;
    private final NotificationsApi notifications;
    private final PasswordPolicyEnforcer policy;
    private final PasswordHasher hasher;
    private final SessionService sessions;
    private final CurrentUserProvider currentUser;
    private final Messages messages;
    private final AuditLog audit;
    private final String publicBaseUrl;
    private final Duration resetTtl;

    public PasswordService(UserRepository users, OrgApi org, OneTimeTokens tokens, NotificationsApi notifications,
                           PasswordPolicyEnforcer policy, PasswordHasher hasher, SessionService sessions,
                           CurrentUserProvider currentUser, Messages messages, AuditLog audit, AppProperties properties) {
        this.users = users;
        this.org = org;
        this.tokens = tokens;
        this.notifications = notifications;
        this.policy = policy;
        this.hasher = hasher;
        this.sessions = sessions;
        this.currentUser = currentUser;
        this.messages = messages;
        this.audit = audit;
        this.publicBaseUrl = properties.publicBaseUrl();
        this.resetTtl = properties.security().passwordResetTtl();
    }

    /** Всегда завершается успешно: существование аккаунта не раскрывается. */
    @Transactional
    public void requestReset(String email, String tenantSlug) {
        if (!EmailAddress.isValid(email)) {
            return;
        }
        candidates(EmailAddress.normalize(email), tenantSlug).stream()
                .filter(UserAccount::isActive)
                .forEach(this::sendResetLink);
    }

    @Transactional
    public void reset(String rawToken, String newPassword) {
        OneTimeToken token = tokens.requireValid(Kind.PASSWORD_RESET, rawToken);
        UserAccount user = users.findById(token.tenantId(), token.userId())
                .filter(UserAccount::isActive)
                .orElseThrow(() -> new BusinessRuleException(IdentityErrors.TOKEN_INVALID, "Link is invalid or expired"));
        String hash = policy.validateAndHash(user.tenantId(), newPassword, NEW_PASSWORD_FIELD);
        tokens.consume(Kind.PASSWORD_RESET, token);
        users.updatePassword(user.tenantId(), user.id(), hash);
        sessions.revokeAll(user.tenantId(), user.id());
        audit.record(AuditRecord.of(user.tenantId(), user.id(), "auth.password_reset", "user", user.id().toString()));
    }

    /**
     * Смена пароля текущим пользователем: все сессии отзываются, текущему клиенту выдаётся новая
     * (refresh-cookie в ответе), чтобы «разлогинить» только остальные устройства.
     */
    @Transactional
    public IssuedSession change(String currentPassword, String newPassword) {
        CurrentUser current = currentUser.require();
        UserAccount user = users.findById(current.tenantId(), current.userId())
                .orElseThrow(() -> new NotFoundException(IdentityErrors.USER_NOT_FOUND, "User not found"));
        if (user.hasPassword() && !hasher.matches(currentPassword == null ? "" : currentPassword, user.passwordHash())) {
            throw ValidationException.single(CURRENT_PASSWORD_FIELD, "invalid", messages.get("identity.password.current_invalid"));
        }
        String hash = policy.validateAndHash(user.tenantId(), newPassword, NEW_PASSWORD_FIELD);
        users.updatePassword(user.tenantId(), user.id(), hash);
        sessions.revokeAll(user.tenantId(), user.id());
        audit.record(AuditRecord.of(user.tenantId(), user.id(), "auth.password_changed", "user", user.id().toString()));
        return sessions.open(user, LoginMethod.PASSWORD_CHANGE);
    }

    private List<UserAccount> candidates(String email, String tenantSlug) {
        if (tenantSlug == null || tenantSlug.isBlank()) {
            return users.findAllByEmail(email);
        }
        return org.findBySlug(tenantSlug.trim())
                .flatMap(tenant -> users.findByEmail(tenant.id(), email))
                .map(List::of)
                .orElse(List.of());
    }

    private void sendResetLink(UserAccount user) {
        IssuedToken token = tokens.issue(Kind.PASSWORD_RESET, user.tenantId(), user.id(), resetTtl);
        String link = publicBaseUrl + RESET_PATH + token.value();
        notifications.notify(new NotificationCommand(user.tenantId(), List.of(user.id()), NotificationCategory.ACCOUNT,
                RESET_MESSAGE, List.<Object>of(link, resetTtl.toMinutes()), link, RESET_MESSAGE + ":" + token.id(), true, null));
        audit.record(AuditRecord.of(user.tenantId(), user.id(), "auth.password_reset_requested", "user", user.id().toString()));
    }
}
