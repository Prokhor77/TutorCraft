package com.tutorcraft.core.identity.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.identity.application.UserRepository.NewUser;
import com.tutorcraft.core.identity.domain.AccountOrigin;
import com.tutorcraft.core.identity.domain.EmailAddress;
import com.tutorcraft.core.identity.domain.UserAccount;
import com.tutorcraft.core.identity.domain.UserStatus;
import com.tutorcraft.core.org.OrgApi;
import com.tutorcraft.core.org.OrgApi.TenantInfo;
import com.tutorcraft.core.shared.config.AppProperties;
import com.tutorcraft.core.shared.domain.Ids;
import com.tutorcraft.core.shared.security.PasswordHasher;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Главный администратор платформы из конфигурации (ADMIN_EMAIL / ADMIN_PASSWORD в .env). При каждом старте:
 * служебный tenant, учётная запись (пароль и статус приводятся к конфигурации — смена пароля в .env действует
 * после перезапуска) и роль platform_admin, которая снимается со всех остальных. Выдать её через API нельзя.
 */
@Component
class PlatformAdminBootstrapper {

    /** После SystemRolesInitializer (@Order(0)), до демо-сида (@Order(10)). */
    private static final int AFTER_SYSTEM_ROLES = 5;
    private static final String PASSWORD_FIELD = "ADMIN_PASSWORD";
    private static final Logger log = LoggerFactory.getLogger(PlatformAdminBootstrapper.class);

    private final AppProperties.Admin admin;
    private final OrgApi org;
    private final UserRepository users;
    private final AccessService access;
    private final PasswordPolicyEnforcer passwords;
    private final PasswordHasher hasher;
    private final SessionService sessions;
    private final TransactionTemplate transaction;

    PlatformAdminBootstrapper(AppProperties properties, OrgApi org, UserRepository users, AccessService access,
                              PasswordPolicyEnforcer passwords, PasswordHasher hasher, SessionService sessions,
                              PlatformTransactionManager transactionManager) {
        this.admin = properties.admin();
        this.org = org;
        this.users = users;
        this.access = access;
        this.passwords = passwords;
        this.hasher = hasher;
        this.sessions = sessions;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(AFTER_SYSTEM_ROLES)
    public void ensureAdmin() {
        if (!admin.configured()) {
            log.warn("Platform administrator is not configured: set ADMIN_EMAIL and ADMIN_PASSWORD in .env");
            return;
        }
        transaction.executeWithoutResult(status -> {
            TenantInfo tenant = org.ensurePlatformTenant();
            String email = EmailAddress.normalize(admin.email());
            UserAccount account = users.findByEmail(tenant.id(), email)
                    .map(existing -> syncExisting(tenant, existing))
                    .orElseGet(() -> create(tenant, email));
            access.ensureSolePlatformAdmin(tenant.id(), account.id());
            log.info("Platform administrator {} is ready (user {})", email, account.id());
        });
    }

    private UserAccount create(TenantInfo tenant, String email) {
        String hash = passwords.validateAndHash(tenant.id(), admin.password(), PASSWORD_FIELD);
        UUID id = Ids.newId();
        users.insert(new NewUser(id, tenant.id(), email, hash, admin.firstName(), admin.lastName(),
                tenant.defaultTimezone(), tenant.defaultLocale(), UserStatus.ACTIVE, null, null, null, AccountOrigin.SYSTEM));
        return users.findById(tenant.id(), id).orElseThrow(() -> new IllegalStateException("Admin was not persisted"));
    }

    private UserAccount syncExisting(TenantInfo tenant, UserAccount existing) {
        boolean passwordChanged = !existing.hasPassword() || !hasher.matches(admin.password(), existing.passwordHash());
        if (passwordChanged) {
            users.updatePassword(tenant.id(), existing.id(), passwords.validateAndHash(tenant.id(), admin.password(), PASSWORD_FIELD));
            sessions.revokeAll(tenant.id(), existing.id());
            log.info("Platform administrator password updated from configuration; sessions revoked");
        }
        if (existing.status() != UserStatus.ACTIVE) {
            users.updateStatus(tenant.id(), existing.id(), UserStatus.ACTIVE);
        }
        return existing;
    }
}
