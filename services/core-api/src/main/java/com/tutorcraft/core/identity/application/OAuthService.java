package com.tutorcraft.core.identity.application;

import com.tutorcraft.core.identity.application.AccountProvisioner.Signup;
import com.tutorcraft.core.identity.application.GoogleTokenVerifier.GoogleIdentity;
import com.tutorcraft.core.identity.application.SessionService.LoginMethod;
import com.tutorcraft.core.identity.domain.EmailAddress;
import com.tutorcraft.core.identity.domain.TelegramLoginVerifier;
import com.tutorcraft.core.identity.domain.UserAccount;
import com.tutorcraft.core.identity.domain.UserStatus;
import com.tutorcraft.core.org.OrgApi;
import com.tutorcraft.core.shared.config.AppProperties;
import com.tutorcraft.core.shared.domain.NotFoundException;
import com.tutorcraft.core.shared.domain.UnauthorizedException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Вход через Google (OIDC ID-token) и Telegram Login Widget (FR-AUTH-HYB-01, ADR-002).
 * Без tenantSlug неизвестный пользователь становится владельцем нового tenant (репетитор);
 * с tenantSlug — участником этого tenant без ролей (студент).
 */
@Service
public class OAuthService {

    private static final Logger log = LoggerFactory.getLogger(OAuthService.class);
    private static final String TELEGRAM_ID_FIELD = "id";
    private static final String TELEGRAM_EMAIL_TEMPLATE = "tg-%d@telegram.invalid";

    private final AppProperties.OAuth settings;
    private final GoogleTokenVerifier googleVerifier;
    private final TelegramLoginVerifier telegramVerifier;
    private final UserRepository users;
    private final OrgApi org;
    private final AccountProvisioner provisioner;
    private final TenantChoice tenantChoice;
    private final SignInGuard signInGuard;
    private final SessionService sessions;
    private final AuthService auth;
    private final Clock clock;

    public OAuthService(AppProperties properties, GoogleTokenVerifier googleVerifier, UserRepository users, OrgApi org,
                        AccountProvisioner provisioner, TenantChoice tenantChoice, SignInGuard signInGuard,
                        SessionService sessions, AuthService auth, Clock clock) {
        this.settings = properties.oauth();
        this.googleVerifier = googleVerifier;
        this.telegramVerifier = settings.telegramEnabled()
                ? new TelegramLoginVerifier(settings.telegramBotToken(), settings.telegramAuthMaxAge()) : null;
        this.users = users;
        this.org = org;
        this.provisioner = provisioner;
        this.tenantChoice = tenantChoice;
        this.signInGuard = signInGuard;
        this.sessions = sessions;
        this.auth = auth;
        this.clock = clock;
    }

    public ProvidersView providers() {
        GoogleProvider google = settings.googleEnabled() ? new GoogleProvider(settings.googleClientId()) : null;
        TelegramProvider telegram = settings.telegramEnabled() ? new TelegramProvider(settings.telegramBotUsername()) : null;
        return new ProvidersView(google, telegram);
    }

    @Transactional
    public AuthResult google(String idToken, String tenantSlug) {
        if (!settings.googleEnabled()) {
            throw providerDisabled();
        }
        GoogleIdentity identity = googleVerifier.verify(idToken);
        if (!identity.emailVerified() || !EmailAddress.isValid(identity.email())) {
            throw new UnauthorizedException(IdentityErrors.OAUTH_EMAIL_UNVERIFIED, "Google email is not verified");
        }
        UserAccount user = resolve(tenantSlug, tenantId -> googleUserInTenant(tenantId, identity), () -> googleUserAnyTenant(identity));
        return signIn(user, LoginMethod.GOOGLE);
    }

    @Transactional
    public AuthResult telegram(Map<String, String> fields, String tenantSlug) {
        if (telegramVerifier == null) {
            throw providerDisabled();
        }
        TelegramLoginVerifier.Result result = telegramVerifier.verify(fields, clock.instant());
        if (result != TelegramLoginVerifier.Result.VALID) {
            log.info("Telegram login rejected: {}", result);
            throw new UnauthorizedException(IdentityErrors.OAUTH_INVALID, "Telegram login data is invalid");
        }
        TelegramProfile profile = TelegramProfile.of(fields);
        UserAccount user = resolve(tenantSlug, tenantId -> telegramUserInTenant(tenantId, profile), () -> telegramUserAnyTenant(profile));
        return signIn(user, LoginMethod.TELEGRAM);
    }

    private UserAccount resolve(String tenantSlug, Function<UUID, UserAccount> inTenant, Supplier<UserAccount> anyTenant) {
        if (tenantSlug == null || tenantSlug.isBlank()) {
            return anyTenant.get();
        }
        UUID tenantId = org.findBySlug(tenantSlug.trim())
                .orElseThrow(() -> new NotFoundException(IdentityErrors.TENANT_NOT_FOUND, "School not found"))
                .id();
        return inTenant.apply(tenantId);
    }

    /** Первый вход через OAuth создаёт учётную запись: согласие дано по уведомлению под кнопками входа. */
    private UserAccount consented(UserAccount created, LegalConsent.Method method) {
        auth.recordLegalConsent(created, method);
        return created;
    }

    private AuthResult signIn(UserAccount user, LoginMethod method) {
        signInGuard.ensureCanSignIn(user);
        return auth.toResult(sessions.open(user, method));
    }

    private UserAccount googleUserInTenant(UUID tenantId, GoogleIdentity identity) {
        Optional<UserAccount> bySubject = users.findByGoogleSub(tenantId, identity.subject());
        if (bySubject.isPresent()) {
            return bySubject.get();
        }
        return users.findByEmail(tenantId, EmailAddress.normalize(identity.email()))
                .map(user -> linkGoogle(user, identity.subject()))
                .orElseGet(() -> consented(provisioner.createMember(tenantId, googleSignup(identity)), LegalConsent.Method.OAUTH_GOOGLE));
    }

    private UserAccount googleUserAnyTenant(GoogleIdentity identity) {
        List<UserAccount> bySubject = users.findAllByGoogleSub(identity.subject());
        if (!bySubject.isEmpty()) {
            return tenantChoice.single(bySubject);
        }
        List<UserAccount> byEmail = users.findAllByEmail(EmailAddress.normalize(identity.email()));
        if (!byEmail.isEmpty()) {
            return linkGoogle(tenantChoice.single(byEmail), identity.subject());
        }
        return consented(provisioner.createTenantOwner(googleSignup(identity)), LegalConsent.Method.OAUTH_GOOGLE);
    }

    /** Email подтверждён Google — приглашённый пользователь может войти и активируется. */
    private UserAccount linkGoogle(UserAccount user, String subject) {
        if (user.googleSub() == null) {
            users.linkGoogle(user.tenantId(), user.id(), subject);
        }
        if (user.isInvited()) {
            users.updateStatus(user.tenantId(), user.id(), UserStatus.ACTIVE);
        }
        return users.findById(user.tenantId(), user.id()).orElseThrow(() -> new IllegalStateException("User disappeared"));
    }

    private UserAccount telegramUserInTenant(UUID tenantId, TelegramProfile profile) {
        return users.findByTelegramUserId(tenantId, profile.id())
                .orElseGet(() -> consented(provisioner.createMember(tenantId, telegramSignup(profile)), LegalConsent.Method.OAUTH_TELEGRAM));
    }

    private UserAccount telegramUserAnyTenant(TelegramProfile profile) {
        List<UserAccount> existing = users.findAllByTelegramUserId(profile.id());
        return existing.isEmpty()
                ? consented(provisioner.createTenantOwner(telegramSignup(profile)), LegalConsent.Method.OAUTH_TELEGRAM)
                : tenantChoice.single(existing);
    }

    private static Signup googleSignup(GoogleIdentity identity) {
        String email = EmailAddress.normalize(identity.email());
        String firstName = identity.givenName() == null ? email.substring(0, email.indexOf('@')) : identity.givenName();
        String lastName = identity.familyName() == null ? "" : identity.familyName();
        return new Signup(email, null, firstName, lastName, null, identity.subject(), null);
    }

    private static Signup telegramSignup(TelegramProfile profile) {
        String email = TELEGRAM_EMAIL_TEMPLATE.formatted(profile.id());
        return new Signup(email, null, profile.firstName(), profile.lastName(), null, null, profile.id());
    }

    private static UnauthorizedException malformedTelegram() {
        return new UnauthorizedException(IdentityErrors.OAUTH_INVALID, "Telegram login data is invalid");
    }

    private static NotFoundException providerDisabled() {
        return new NotFoundException(IdentityErrors.PROVIDER_DISABLED, "Sign-in provider is disabled");
    }

    private record TelegramProfile(long id, String firstName, String lastName) {

        private static final String FIRST_NAME = "first_name";
        private static final String LAST_NAME = "last_name";
        private static final String USERNAME = "username";

        static TelegramProfile of(Map<String, String> fields) {
            try {
                long id = Long.parseLong(fields.get(TELEGRAM_ID_FIELD));
                String firstName = fields.getOrDefault(FIRST_NAME, fields.getOrDefault(USERNAME, ""));
                return new TelegramProfile(id, firstName, fields.getOrDefault(LAST_NAME, ""));
            } catch (NumberFormatException e) {
                throw malformedTelegram();
            }
        }
    }

    public record ProvidersView(GoogleProvider google, TelegramProvider telegram) {
    }

    public record GoogleProvider(String clientId) {
    }

    public record TelegramProvider(String botUsername) {
    }
}
