package com.tutorcraft.core.identity.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.access.domain.TenantRole;
import com.tutorcraft.core.audit.AuditLog;
import com.tutorcraft.core.audit.AuditRecord;
import com.tutorcraft.core.identity.application.SchoolMembersService.MemberQuery;
import com.tutorcraft.core.identity.application.UserRepository.Anonymized;
import com.tutorcraft.core.identity.application.UserRepository.MemberFilter;
import com.tutorcraft.core.identity.application.UserRepository.MemberView;
import com.tutorcraft.core.identity.spi.UserDataEraser;
import com.tutorcraft.core.org.OrgApi;
import com.tutorcraft.core.org.OrgApi.TenantInfo;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.api.PageResponse;
import com.tutorcraft.core.shared.domain.BusinessRuleException;
import com.tutorcraft.core.shared.domain.NotFoundException;
import com.tutorcraft.core.shared.domain.Validator;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Пользователи всех школ для главного администратора (право platform.manage): кто, в какой школе, кем и как создан;
 * блокировка на уровне платформы (репетитор её не снимает) и полное удаление (FR-USER-05).
 */
@Service
public class PlatformUsersService {

    private static final Logger log = LoggerFactory.getLogger(PlatformUsersService.class);
    private static final int MAX_REASON_LENGTH = 500;
    private static final String ERASED_EMAIL_TEMPLATE = "deleted-%s@deleted.invalid";
    private static final String ERASED_FIRST_NAME = "Удалённый";
    private static final String ERASED_LAST_NAME = "пользователь";

    private final UserRepository users;
    private final SessionService sessions;
    private final List<UserDataEraser> erasers;
    private final OrgApi org;
    private final AccessService access;
    private final CurrentUserProvider currentUser;
    private final AuditLog audit;
    private final Clock clock;

    public PlatformUsersService(UserRepository users, SessionService sessions, List<UserDataEraser> erasers, OrgApi org,
                                AccessService access, CurrentUserProvider currentUser, AuditLog audit, Clock clock) {
        this.users = users;
        this.sessions = sessions;
        this.erasers = List.copyOf(erasers);
        this.org = org;
        this.access = access;
        this.currentUser = currentUser;
        this.audit = audit;
        this.clock = clock;
    }

    /** @param tenantId null — все школы */
    @Transactional(readOnly = true)
    public PageResponse<PlatformUserView> list(UUID tenantId, MemberQuery query, PageQuery page) {
        requirePlatformAdmin();
        MemberQueries.validate(query);
        MemberFilter filter = new MemberFilter(tenantId, MemberQueries.trim(query.q()), query.status(), query.origin());
        PageResponse<MemberView> members = page.toPage(users.searchMembers(filter, page), MemberView::createdAt, MemberView::id);
        Map<UUID, TenantInfo> tenants = new HashMap<>();
        return members.map(member -> new PlatformUserView(member, tenants.computeIfAbsent(member.tenantId(), org::require)));
    }

    @Transactional
    public PlatformUserView block(UUID userId, String reason) {
        CurrentUser actor = requirePlatformAdmin();
        String trimmed = MemberQueries.trim(reason);
        new Validator().maxLength(trimmed, MAX_REASON_LENGTH, "reason").throwIfInvalid();
        MemberView member = requireManageable(actor, userId);
        users.updatePlatformBlock(member.tenantId(), userId, clock.instant(), trimmed);
        sessions.revokeAll(member.tenantId(), userId);
        audit.record(AuditRecord.of(member.tenantId(), actor.userId(), "user.platform_blocked", "user", userId.toString()));
        log.info("User {} blocked by platform administrator", userId);
        return view(userId);
    }

    @Transactional
    public PlatformUserView unblock(UUID userId) {
        CurrentUser actor = requirePlatformAdmin();
        MemberView member = requireManageable(actor, userId);
        users.updatePlatformBlock(member.tenantId(), userId, null, null);
        audit.record(AuditRecord.of(member.tenantId(), actor.userId(), "user.platform_unblocked", "user", userId.toString()));
        log.info("User {} unblocked by platform administrator", userId);
        return view(userId);
    }

    /**
     * Полное удаление: сессии отзываются, каждый модуль стирает данные пользователя, учётная запись обезличивается.
     * Владельца школы удалить нельзя (школа осталась бы без владельца) — его можно заблокировать.
     */
    @Transactional
    public void erase(UUID userId) {
        CurrentUser actor = requirePlatformAdmin();
        MemberView member = requireManageable(actor, userId);
        if (member.isTenantAdmin()) {
            throw new BusinessRuleException(IdentityErrors.CANNOT_ERASE_SCHOOL_OWNER, "School owner cannot be erased");
        }
        UUID tenantId = member.tenantId();
        sessions.revokeAll(tenantId, userId);
        erasers.forEach(eraser -> eraser.eraseUser(tenantId, userId));
        users.anonymize(tenantId, userId, new Anonymized(ERASED_EMAIL_TEMPLATE.formatted(userId), ERASED_FIRST_NAME,
                ERASED_LAST_NAME), clock.instant());
        audit.record(AuditRecord.of(tenantId, actor.userId(), "user.erased", "user", userId.toString())
                .withDiff(Map.of("origin", member.origin(), "modules", erasers.size())));
        log.info("User {} erased by platform administrator ({} data owners)", userId, erasers.size());
    }

    private CurrentUser requirePlatformAdmin() {
        access.require(Permission.PLATFORM_MANAGE, AccessContext.tenant());
        return currentUser.require();
    }

    /** Нельзя действовать над собой и над главным администратором. */
    private MemberView requireManageable(CurrentUser actor, UUID userId) {
        if (userId.equals(actor.userId())) {
            throw new BusinessRuleException(IdentityErrors.CANNOT_MANAGE_SELF, "You cannot apply this to yourself");
        }
        MemberView member = users.member(null, userId).orElseThrow(PlatformUsersService::notFound);
        if (member.tenantRoles().contains(TenantRole.PLATFORM_ADMIN.key())) {
            throw new BusinessRuleException(IdentityErrors.USER_PROTECTED, "Platform administrator cannot be changed");
        }
        return member;
    }

    private PlatformUserView view(UUID userId) {
        MemberView member = users.member(null, userId).orElseThrow(PlatformUsersService::notFound);
        return new PlatformUserView(member, org.require(member.tenantId()));
    }

    private static NotFoundException notFound() {
        return new NotFoundException(IdentityErrors.USER_NOT_FOUND, "User not found");
    }

    public record PlatformUserView(MemberView member, TenantInfo tenant) {
    }
}
