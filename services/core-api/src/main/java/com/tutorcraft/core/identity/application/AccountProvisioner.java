package com.tutorcraft.core.identity.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.TenantRole;
import com.tutorcraft.core.audit.AuditLog;
import com.tutorcraft.core.audit.AuditRecord;
import com.tutorcraft.core.identity.application.UserRepository.NewUser;
import com.tutorcraft.core.identity.domain.AccountOrigin;
import com.tutorcraft.core.identity.domain.UserAccount;
import com.tutorcraft.core.identity.domain.UserStatus;
import com.tutorcraft.core.org.OrgApi;
import com.tutorcraft.core.org.OrgApi.TenantInfo;
import com.tutorcraft.core.shared.domain.Ids;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Создание учётных записей при самостоятельной регистрации: владелец нового tenant (репетитор, ADR-002)
 * или участник существующего tenant без ролей (студент). Вызывать внутри транзакции.
 */
@Component
public class AccountProvisioner {

    private static final Logger log = LoggerFactory.getLogger(AccountProvisioner.class);
    private static final String PASSWORD_FIELD = "password";
    private static final char EMAIL_AT = '@';

    private final OrgApi org;
    private final UserRepository users;
    private final AccessService access;
    private final PasswordPolicyEnforcer passwords;
    private final AuditLog audit;

    public AccountProvisioner(OrgApi org, UserRepository users, AccessService access, PasswordPolicyEnforcer passwords,
                              AuditLog audit) {
        this.org = org;
        this.users = users;
        this.access = access;
        this.passwords = passwords;
        this.audit = audit;
    }

    /** Новый tenant и его владелец с ролью tenant_admin. */
    public UserAccount createTenantOwner(Signup signup) {
        TenantInfo tenant = org.createTenant(tenantName(signup), null);
        UserAccount owner = insert(tenant, signup, AccountOrigin.SCHOOL_OWNER);
        access.replaceTenantRoles(tenant.id(), owner.id(), Set.of(TenantRole.TENANT_ADMIN), owner.id());
        audit.record(AuditRecord.of(tenant.id(), owner.id(), "tenant.registered", "tenant", tenant.id().toString()));
        log.info("Tenant {} registered with owner {}", tenant.id(), owner.id());
        return owner;
    }

    /** Участник tenant без ролей (роль в курсе появится при записи). */
    public UserAccount createMember(UUID tenantId, Signup signup) {
        UserAccount member = insert(org.require(tenantId), signup, AccountOrigin.SELF_SIGNUP);
        audit.record(AuditRecord.of(tenantId, member.id(), "user.self_registered", "user", member.id().toString()));
        return member;
    }

    private UserAccount insert(TenantInfo tenant, Signup signup, AccountOrigin origin) {
        String hash = signup.password() == null ? null : passwords.validateAndHash(tenant.id(), signup.password(), PASSWORD_FIELD);
        UUID id = Ids.newId();
        users.insert(new NewUser(id, tenant.id(), signup.email(), hash, signup.firstName(), signup.lastName(),
                tenant.defaultTimezone(), tenant.defaultLocale(), UserStatus.ACTIVE, signup.googleSub(), signup.telegramUserId(),
                null, origin));
        return users.findById(tenant.id(), id).orElseThrow(() -> new IllegalStateException("User was not persisted"));
    }

    private static String tenantName(Signup signup) {
        if (signup.schoolName() != null && !signup.schoolName().isBlank()) {
            return signup.schoolName().trim();
        }
        String fullName = (signup.firstName() + " " + signup.lastName()).trim();
        return fullName.isEmpty() ? signup.email().substring(0, signup.email().indexOf(EMAIL_AT)) : fullName;
    }

    /**
     * @param email    нормализованный email
     * @param password исходный пароль или null (вход через Google/Telegram)
     */
    public record Signup(String email, String password, String firstName, String lastName, String schoolName,
                         String googleSub, Long telegramUserId) {

        @Override
        public String toString() {
            return "Signup[googleSub=" + (googleSub != null) + ", telegram=" + (telegramUserId != null) + "]";
        }
    }
}
