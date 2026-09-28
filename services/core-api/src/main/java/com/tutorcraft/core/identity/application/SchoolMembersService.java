package com.tutorcraft.core.identity.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.identity.application.UserRepository.MemberFilter;
import com.tutorcraft.core.identity.application.UserRepository.MemberView;
import com.tutorcraft.core.identity.domain.UserAccount;
import com.tutorcraft.core.identity.domain.UserStatus;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.api.PageResponse;
import com.tutorcraft.core.shared.domain.BusinessRuleException;
import com.tutorcraft.core.shared.domain.NotFoundException;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ученики своей школы для владельца (репетитора) без администратора платформы: список с происхождением учётной
 * записи, блокировка/разблокировка в школе, новая ссылка активации. Права member.view / member.manage.
 * Блокировку платформой репетитор видит, но снять не может; других владельцев школы блокировать нельзя.
 */
@Service
public class SchoolMembersService {

    private static final Set<UserStatus> SETTABLE_STATUSES = EnumSet.of(UserStatus.ACTIVE, UserStatus.SUSPENDED);

    private final UserRepository users;
    private final UserStatusChanger statusChanger;
    private final InvitationService invitations;
    private final AccessService access;
    private final CurrentUserProvider currentUser;

    public SchoolMembersService(UserRepository users, UserStatusChanger statusChanger, InvitationService invitations,
                                AccessService access, CurrentUserProvider currentUser) {
        this.users = users;
        this.statusChanger = statusChanger;
        this.invitations = invitations;
        this.access = access;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public PageResponse<MemberView> list(MemberQuery query, PageQuery page) {
        access.require(Permission.MEMBER_VIEW, AccessContext.tenant());
        MemberQueries.validate(query);
        UUID tenantId = currentUser.require().tenantId();
        MemberFilter filter = new MemberFilter(tenantId, MemberQueries.trim(query.q()), query.status(), query.origin());
        return page.toPage(users.searchMembers(filter, page), MemberView::createdAt, MemberView::id);
    }

    /** Блокировка (suspended) или разблокировка (active) в школе. */
    @Transactional
    public MemberView changeStatus(UUID userId, String statusKey) {
        CurrentUser actor = currentUser.require();
        access.require(Permission.MEMBER_MANAGE, AccessContext.tenant());
        UserStatus status = MemberQueries.parseSettableStatus(statusKey, SETTABLE_STATUSES);
        MemberView member = requireMember(actor.tenantId(), userId);
        if (member.isTenantAdmin()) {
            throw new BusinessRuleException(IdentityErrors.USER_PROTECTED, "School owners cannot be blocked here");
        }
        statusChanger.change(actor, requireAccount(actor.tenantId(), userId), status);
        return requireMember(actor.tenantId(), userId);
    }

    /** Новая ссылка активации для ещё не принявшего приглашение (предыдущие аннулируются). */
    @Transactional
    public String reissueActivationLink(UUID userId) {
        CurrentUser actor = currentUser.require();
        access.require(Permission.MEMBER_MANAGE, AccessContext.tenant());
        UserAccount user = requireAccount(actor.tenantId(), userId);
        if (!user.isInvited()) {
            throw new BusinessRuleException(IdentityErrors.NOT_INVITED, "User has already accepted the invitation");
        }
        if (user.isPlatformBlocked()) {
            throw new BusinessRuleException(IdentityErrors.USER_BLOCKED, "User is blocked by the platform");
        }
        return invitations.sendInvitation(user, actor.userId());
    }

    private MemberView requireMember(UUID tenantId, UUID userId) {
        return users.member(tenantId, userId).orElseThrow(SchoolMembersService::notFound);
    }

    private UserAccount requireAccount(UUID tenantId, UUID userId) {
        return users.findById(tenantId, userId).orElseThrow(SchoolMembersService::notFound);
    }

    private static NotFoundException notFound() {
        return new NotFoundException(IdentityErrors.USER_NOT_FOUND, "User not found");
    }

    /** Фильтр списка: q — подстрока имени/email; status — active|suspended|invited|blocked; origin — AccountOrigin. */
    public record MemberQuery(String q, String status, String origin) {
    }
}
