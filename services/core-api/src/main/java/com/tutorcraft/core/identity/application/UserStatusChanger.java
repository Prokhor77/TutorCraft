package com.tutorcraft.core.identity.application;

import com.tutorcraft.core.audit.AuditLog;
import com.tutorcraft.core.audit.AuditRecord;
import com.tutorcraft.core.identity.domain.UserAccount;
import com.tutorcraft.core.identity.domain.UserStatus;
import com.tutorcraft.core.shared.domain.BusinessRuleException;
import com.tutorcraft.core.shared.domain.ValidationException;
import com.tutorcraft.core.shared.security.CurrentUser;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Блокировка/разблокировка учётной записи в школе (users.status). Общие правила для админки и репетитора:
 * приглашённого нельзя приостановить до принятия приглашения, себя — нельзя, приостановка отзывает все сессии.
 * Вызывать в транзакции после проверки прав.
 */
@Component
public class UserStatusChanger {

    private static final String STATUS_FIELD = "status";

    private final UserRepository users;
    private final SessionService sessions;
    private final AuditLog audit;

    public UserStatusChanger(UserRepository users, SessionService sessions, AuditLog audit) {
        this.users = users;
        this.sessions = sessions;
        this.audit = audit;
    }

    public void change(CurrentUser actor, UserAccount user, UserStatus status) {
        if (status == user.status()) {
            return;
        }
        if (user.isInvited() || status == UserStatus.INVITED) {
            throw ValidationException.single(STATUS_FIELD, "invalid_transition", "Invited user must accept the invitation first");
        }
        if (status == UserStatus.SUSPENDED && user.id().equals(actor.userId())) {
            throw new BusinessRuleException(IdentityErrors.CANNOT_SUSPEND_SELF, "You cannot suspend yourself");
        }
        users.updateStatus(user.tenantId(), user.id(), status);
        if (status == UserStatus.SUSPENDED) {
            sessions.revokeAll(user.tenantId(), user.id());
        }
        audit.record(AuditRecord.of(user.tenantId(), actor.userId(), "user.status_changed", "user", user.id().toString())
                .withDiff(Map.of("before", user.status().key(), "after", status.key())));
    }
}
