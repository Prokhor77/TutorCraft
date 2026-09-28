package com.tutorcraft.core.identity.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.access.domain.TenantRole;
import com.tutorcraft.core.audit.AuditLog;
import com.tutorcraft.core.audit.AuditRecord;
import com.tutorcraft.core.identity.application.UserRepository.UserFilter;
import com.tutorcraft.core.identity.application.UserRepository.UserSummaryView;
import com.tutorcraft.core.identity.domain.EmailAddress;
import com.tutorcraft.core.identity.domain.UserAccount;
import com.tutorcraft.core.identity.domain.UserStatus;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.api.PageResponse;
import com.tutorcraft.core.shared.domain.BusinessRuleException;
import com.tutorcraft.core.shared.domain.ConflictException;
import com.tutorcraft.core.shared.domain.NotFoundException;
import com.tutorcraft.core.shared.domain.ValidationException;
import com.tutorcraft.core.shared.domain.Validator;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Управление пользователями tenant (FR-USER-01/03). Права: user.view / user.manage / role.manage. */
@Service
public class UserAdminService {

    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_QUERY_LENGTH = 100;
    private static final Set<TenantRole> ASSIGNABLE_ROLES = EnumSet.of(TenantRole.TENANT_ADMIN);
    private static final Set<UserStatus> PATCHABLE_STATUSES = EnumSet.of(UserStatus.ACTIVE, UserStatus.SUSPENDED);

    private final UserRepository users;
    private final AccessService access;
    private final CurrentUserProvider currentUser;
    private final InvitationService invitations;
    private final SessionService sessions;
    private final InvitedUsers invitedUsers;
    private final AuditLog audit;

    public UserAdminService(UserRepository users, AccessService access, CurrentUserProvider currentUser,
                            InvitationService invitations, SessionService sessions, InvitedUsers invitedUsers,
                            AuditLog audit) {
        this.users = users;
        this.access = access;
        this.currentUser = currentUser;
        this.invitations = invitations;
        this.sessions = sessions;
        this.invitedUsers = invitedUsers;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserSummaryView> list(UserFilter filter, PageQuery page) {
        access.require(Permission.USER_VIEW, AccessContext.tenant());
        validate(filter);
        UUID tenantId = currentUser.require().tenantId();
        return page.toPage(users.search(tenantId, filter, page), UserSummaryView::createdAt, UserSummaryView::id);
    }

    @Transactional(readOnly = true)
    public UserSummaryView get(UUID userId) {
        access.require(Permission.USER_VIEW, AccessContext.tenant());
        return summary(currentUser.require().tenantId(), userId);
    }

    @Transactional
    public UserSummaryView create(CreateUserCommand command) {
        CurrentUser actor = currentUser.require();
        access.require(Permission.USER_MANAGE, AccessContext.tenant());
        validate(command);
        Set<TenantRole> roles = parseRoles(command.tenantRoles());
        String email = EmailAddress.normalize(command.email());
        if (users.findByEmail(actor.tenantId(), email).isPresent()) {
            throw new ConflictException(IdentityErrors.EMAIL_TAKEN, "User with this email already exists");
        }
        UserAccount user = invitedUsers.create(actor.tenantId(), email, command.firstName().trim(), command.lastName().trim());
        applyRoles(actor, user, roles);
        audit.record(AuditRecord.of(actor.tenantId(), actor.userId(), "user.created", "user", user.id().toString()));
        if (command.sendInvite()) {
            invitations.sendInvitation(user, actor.userId());
        }
        return summary(actor.tenantId(), user.id());
    }

    @Transactional
    public UserSummaryView update(UUID userId, UpdateUserCommand command) {
        CurrentUser actor = currentUser.require();
        access.require(Permission.USER_MANAGE, AccessContext.tenant());
        validate(command);
        UserAccount user = requireUser(actor.tenantId(), userId);
        if (command.firstName() != null || command.lastName() != null) {
            users.updateNames(user.tenantId(), user.id(), trimOr(command.firstName(), user.firstName()),
                    trimOr(command.lastName(), user.lastName()));
        }
        if (command.status() != null) {
            changeStatus(actor, user, parseStatus(command.status()));
        }
        if (command.tenantRoles() != null) {
            changeRoles(actor, user, parseRoles(command.tenantRoles()));
        }
        audit.record(AuditRecord.of(actor.tenantId(), actor.userId(), "user.updated", "user", user.id().toString()));
        return summary(actor.tenantId(), user.id());
    }

    @Transactional
    public void resendInvitation(UUID userId) {
        CurrentUser actor = currentUser.require();
        access.require(Permission.USER_MANAGE, AccessContext.tenant());
        UserAccount user = requireUser(actor.tenantId(), userId);
        if (!user.isInvited()) {
            throw new BusinessRuleException(IdentityErrors.NOT_INVITED, "User has already accepted the invitation");
        }
        invitations.sendInvitation(user, actor.userId());
    }

    private void changeStatus(CurrentUser actor, UserAccount user, UserStatus status) {
        if (status == user.status()) {
            return;
        }
        if (user.isInvited()) {
            throw ValidationException.single("status", "invalid_transition", "Invited user must accept the invitation first");
        }
        if (status == UserStatus.SUSPENDED && user.id().equals(actor.userId())) {
            throw new BusinessRuleException(IdentityErrors.CANNOT_SUSPEND_SELF, "You cannot suspend yourself");
        }
        users.updateStatus(user.tenantId(), user.id(), status);
        if (status == UserStatus.SUSPENDED) {
            sessions.revokeAll(user.tenantId(), user.id());
        }
        audit.record(AuditRecord.of(actor.tenantId(), actor.userId(), "user.status_changed", "user", user.id().toString())
                .withDiff(Map.of("before", user.status().key(), "after", status.key())));
    }

    private void changeRoles(CurrentUser actor, UserAccount user, Set<TenantRole> roles) {
        boolean demotingSelf = user.id().equals(actor.userId()) && !roles.contains(TenantRole.TENANT_ADMIN)
                && access.tenantRoles(user.tenantId(), user.id()).contains(TenantRole.TENANT_ADMIN);
        if (demotingSelf) {
            throw new BusinessRuleException(IdentityErrors.CANNOT_DEMOTE_SELF, "You cannot remove your own administrator role");
        }
        access.require(Permission.ROLE_MANAGE, AccessContext.tenant());
        access.replaceTenantRoles(user.tenantId(), user.id(), roles, actor.userId());
    }

    private void applyRoles(CurrentUser actor, UserAccount user, Set<TenantRole> roles) {
        if (roles.isEmpty()) {
            return;
        }
        access.require(Permission.ROLE_MANAGE, AccessContext.tenant());
        access.replaceTenantRoles(user.tenantId(), user.id(), roles, actor.userId());
    }

    private UserAccount requireUser(UUID tenantId, UUID userId) {
        return users.findById(tenantId, userId).orElseThrow(UserAdminService::notFound);
    }

    private UserSummaryView summary(UUID tenantId, UUID userId) {
        return users.summary(tenantId, userId).orElseThrow(UserAdminService::notFound);
    }

    private static NotFoundException notFound() {
        return new NotFoundException(IdentityErrors.USER_NOT_FOUND, "User not found");
    }

    private static Set<TenantRole> parseRoles(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return EnumSet.noneOf(TenantRole.class);
        }
        Set<TenantRole> roles = keys.stream().map(TenantRole::fromKey)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(TenantRole.class)));
        if (!ASSIGNABLE_ROLES.containsAll(roles)) {
            throw ValidationException.single("tenantRoles", "not_assignable", "Only tenant_admin can be assigned here");
        }
        return roles;
    }

    private static UserStatus parseStatus(String key) {
        return UserStatus.find(key).filter(PATCHABLE_STATUSES::contains)
                .orElseThrow(() -> ValidationException.single("status", "invalid", "Allowed: active, suspended"));
    }

    private static void validate(UserFilter filter) {
        new Validator()
            .maxLength(filter.query(), MAX_QUERY_LENGTH, "q")
            .check(filter.status() == null || UserStatus.find(filter.status()).isPresent(), "status", "invalid", "Unknown status")
            .check(filter.roleKey() == null || TenantRole.find(filter.roleKey()).isPresent(), "role", "invalid", "Unknown role")
            .throwIfInvalid();
    }

    private static void validate(CreateUserCommand command) {
        new Validator()
            .check(EmailAddress.isValid(command.email()), "email", "invalid_email", "Invalid email")
            .notBlank(command.firstName(), "firstName")
            .maxLength(command.firstName(), MAX_NAME_LENGTH, "firstName")
            .notBlank(command.lastName(), "lastName")
            .maxLength(command.lastName(), MAX_NAME_LENGTH, "lastName")
            .throwIfInvalid();
    }

    private static void validate(UpdateUserCommand command) {
        new Validator()
            .check(command.firstName() == null || !command.firstName().isBlank(), "firstName", "required", "Field is required")
            .maxLength(command.firstName(), MAX_NAME_LENGTH, "firstName")
            .check(command.lastName() == null || !command.lastName().isBlank(), "lastName", "required", "Field is required")
            .maxLength(command.lastName(), MAX_NAME_LENGTH, "lastName")
            .throwIfInvalid();
    }

    private static String trimOr(String value, String fallback) {
        return value == null ? fallback : value.trim();
    }

    public record CreateUserCommand(String email, String firstName, String lastName, List<String> tenantRoles,
                                    boolean sendInvite) {
    }

    public record UpdateUserCommand(String firstName, String lastName, String status, List<String> tenantRoles) {
    }
}
