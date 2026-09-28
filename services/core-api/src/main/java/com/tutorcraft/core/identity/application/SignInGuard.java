package com.tutorcraft.core.identity.application;

import com.tutorcraft.core.identity.domain.UserAccount;
import com.tutorcraft.core.org.OrgApi;
import com.tutorcraft.core.shared.domain.ForbiddenException;
import com.tutorcraft.core.shared.domain.UnauthorizedException;
import org.springframework.stereotype.Component;

/** Общие правила допуска ко входу: нет блокировки платформой, пользователь и tenant не приостановлены, приглашение принято. */
@Component
public class SignInGuard {

    private final OrgApi org;

    public SignInGuard(OrgApi org) {
        this.org = org;
    }

    public void ensureCanSignIn(UserAccount user) {
        if (user.isInvited()) {
            throw new UnauthorizedException(IdentityErrors.INVALID_CREDENTIALS, "Invitation is not accepted yet");
        }
        if (user.isPlatformBlocked()) {
            throw new ForbiddenException(IdentityErrors.ACCOUNT_BLOCKED, "Account is blocked by the platform");
        }
        if (user.isSuspended() || !org.require(user.tenantId()).active()) {
            throw new ForbiddenException(IdentityErrors.ACCOUNT_SUSPENDED, "Account is suspended");
        }
    }
}
