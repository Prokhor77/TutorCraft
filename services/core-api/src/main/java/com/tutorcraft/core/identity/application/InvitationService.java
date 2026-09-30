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
import com.tutorcraft.core.identity.domain.UserAccount;
import com.tutorcraft.core.org.OrgApi;
import com.tutorcraft.core.shared.config.AppProperties;
import com.tutorcraft.core.shared.domain.BusinessRuleException;
import com.tutorcraft.core.shared.domain.Validator;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Приглашения пользователей по email со ссылкой установки пароля (FR-USER-03). */
@Service
public class InvitationService {

    private static final String ACCEPT_PATH = "/accept-invite?token=";
    private static final String INVITATION_MESSAGE = "identity.invitation";
    private static final String COURSE_INVITATION_MESSAGE = "identity.course_invitation";
    private static final long MIN_TTL_DAYS = 1;
    private static final String PASSWORD_FIELD = "password";
    private static final int MAX_NAME_LENGTH = 100;

    private final OneTimeTokens tokens;
    private final NotificationsApi notifications;
    private final OrgApi org;
    private final UserRepository users;
    private final PasswordPolicyEnforcer policy;
    private final SessionService sessions;
    private final AuthService auth;
    private final AuditLog audit;
    private final String publicBaseUrl;
    private final Duration invitationTtl;

    public InvitationService(OneTimeTokens tokens, NotificationsApi notifications, OrgApi org, UserRepository users,
                             PasswordPolicyEnforcer policy, SessionService sessions, AuthService auth, AuditLog audit,
                             AppProperties properties) {
        this.tokens = tokens;
        this.notifications = notifications;
        this.org = org;
        this.users = users;
        this.policy = policy;
        this.sessions = sessions;
        this.auth = auth;
        this.audit = audit;
        this.publicBaseUrl = properties.publicBaseUrl();
        this.invitationTtl = properties.security().invitationTtl();
    }

    /**
     * Новая ссылка-приглашение в школу (предыдущие аннулируются) и письмо со ссылкой. Вызывать в транзакции операции
     * над пользователем.
     *
     * @return ссылка активации — показывается только пригласившему (письмо может не дойти, если SMTP не настроен)
     */
    @Transactional
    public String sendInvitation(UserAccount user, UUID actorId) {
        return issue(user, actorId, INVITATION_MESSAGE, List.<Object>of(tenantName(user), ttlDays()));
    }

    /**
     * То же, что {@link #sendInvitation}, но письмо называет курс и того, кто пригласил (приглашение репетитором).
     *
     * @param courseTitle название курса, на который пользователь уже записан
     */
    @Transactional
    public String sendCourseInvitation(UserAccount user, UUID actorId, String courseTitle) {
        String tenantName = tenantName(user);
        String inviter = users.findById(user.tenantId(), actorId)
                .map(InvitationService::fullName)
                .filter(name -> !name.isBlank())
                .orElse(tenantName);
        return issue(user, actorId, COURSE_INVITATION_MESSAGE,
                List.<Object>of(tenantName, courseTitle, inviter, ttlDays()));
    }

    /** Ссылка сама в текст письма не входит: notifier рисует кнопку {@code <code>.action} и запасную ссылку под ней. */
    private String issue(UserAccount user, UUID actorId, String messageCode, List<Object> args) {
        IssuedToken token = tokens.issue(Kind.INVITATION, user.tenantId(), user.id(), invitationTtl);
        String link = publicBaseUrl + ACCEPT_PATH + token.value();
        notifications.notify(new NotificationCommand(user.tenantId(), List.of(user.id()), NotificationCategory.ACCOUNT,
                messageCode, args, link, messageCode + ":" + token.id(), true, user.email()));
        audit.record(AuditRecord.of(user.tenantId(), actorId, "user.invited", "user", user.id().toString()));
        return link;
    }

    private String tenantName(UserAccount user) {
        return org.require(user.tenantId()).name();
    }

    private long ttlDays() {
        return Math.max(MIN_TTL_DAYS, invitationTtl.toDays());
    }

    private static String fullName(UserAccount account) {
        return (nullToEmpty(account.firstName()) + " " + nullToEmpty(account.lastName())).strip();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    @Transactional
    public AuthResult accept(AcceptCommand command) {
        validate(command);
        OneTimeToken token = tokens.requireValid(Kind.INVITATION, command.token());
        UserAccount user = users.findById(token.tenantId(), token.userId())
                .filter(UserAccount::isInvited)
                .filter(candidate -> !candidate.isPlatformBlocked())
                .orElseThrow(() -> new BusinessRuleException(IdentityErrors.TOKEN_INVALID, "Invitation is invalid or expired"));
        String hash = policy.validateAndHash(user.tenantId(), command.password(), PASSWORD_FIELD);
        tokens.consume(Kind.INVITATION, token);
        users.activate(user.tenantId(), user.id(), hash, command.firstName().trim(), command.lastName().trim());
        audit.record(AuditRecord.of(user.tenantId(), user.id(), "user.invitation_accepted", "user", user.id().toString()));
        auth.recordLegalConsent(user, LegalConsent.Method.INVITATION);
        UserAccount active = users.findById(user.tenantId(), user.id()).orElseThrow(() -> new IllegalStateException("User disappeared"));
        return auth.toResult(sessions.open(active, LoginMethod.INVITATION));
    }

    private static void validate(AcceptCommand command) {
        new Validator()
            .notBlank(command.token(), "token")
            .notBlank(command.firstName(), "firstName")
            .maxLength(command.firstName(), MAX_NAME_LENGTH, "firstName")
            .notBlank(command.lastName(), "lastName")
            .maxLength(command.lastName(), MAX_NAME_LENGTH, "lastName")
            .check(command.acceptTerms(), LegalConsent.FIELD, LegalConsent.REQUIRED_CODE, LegalConsent.REQUIRED_MESSAGE)
            .throwIfInvalid();
    }

    public record AcceptCommand(String token, String password, String firstName, String lastName, boolean acceptTerms) {

        @Override
        public String toString() {
            return "AcceptCommand[***]";
        }
    }
}
