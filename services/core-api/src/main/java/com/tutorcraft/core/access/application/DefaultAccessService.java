package com.tutorcraft.core.access.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.access.domain.PermissionResolver;
import com.tutorcraft.core.access.domain.RoleGrant;
import com.tutorcraft.core.access.domain.RoleScope;
import com.tutorcraft.core.access.domain.TenantRole;
import com.tutorcraft.core.access.spi.CategoryAncestry;
import com.tutorcraft.core.access.spi.CourseLocator;
import com.tutorcraft.core.access.spi.CourseMembershipResolver;
import com.tutorcraft.core.audit.AuditLog;
import com.tutorcraft.core.audit.AuditRecord;
import com.tutorcraft.core.shared.domain.ForbiddenException;
import com.tutorcraft.core.shared.domain.NotFoundException;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DefaultAccessService implements AccessService {

    private static final String COURSE_NOT_FOUND = "course.not_found";

    private final RoleRepository roles;
    private final CourseLocator courses;
    private final CourseMembershipResolver memberships;
    private final CategoryAncestry categories;
    private final CurrentUserProvider currentUser;
    private final AuditLog audit;

    DefaultAccessService(RoleRepository roles, CourseLocator courses, CourseMembershipResolver memberships,
                         CategoryAncestry categories, CurrentUserProvider currentUser, AuditLog audit) {
        this.roles = roles;
        this.courses = courses;
        this.memberships = memberships;
        this.categories = categories;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @Override
    public Set<Permission> permissions(AccessContext context) {
        CurrentUser user = currentUser.require();
        return permissionsOf(user.tenantId(), user.userId(), context);
    }

    @Override
    public boolean can(Permission permission, AccessContext context) {
        return permissions(context).contains(permission);
    }

    @Override
    public void require(Permission permission, AccessContext context) {
        if (!can(permission, context)) {
            throw new ForbiddenException("access.denied", "Missing permission " + permission.key(),
                    Map.of("permission", permission.key()));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Permission> permissionsOf(UUID tenantId, UUID userId, AccessContext context) {
        PermissionResolver resolver = new PermissionResolver(roles.rolePermissions(tenantId));
        List<RoleGrant> grants = roles.grantsOf(tenantId, userId);
        return switch (context.type()) {
            case TENANT -> resolver.resolve(tenantLevel(grants), List.of(), Optional.empty());
            case CATEGORY -> resolver.resolve(grants, categories.selfAndAncestors(tenantId, context.id()), Optional.empty());
            case COURSE -> coursePermissions(resolver, grants, tenantId, userId, context.id());
        };
    }

    private Set<Permission> coursePermissions(PermissionResolver resolver, List<RoleGrant> grants,
                                              UUID tenantId, UUID userId, UUID courseId) {
        Optional<Optional<UUID>> located = courses.categoryOf(tenantId, courseId);
        if (located.isEmpty()) {
            auditCrossTenantProbe(tenantId, userId, courseId);
            throw new NotFoundException(COURSE_NOT_FOUND, "Course not found");
        }
        Optional<UUID> categoryId = located.get();
        List<UUID> ancestry = categoryId.map(id -> categories.selfAndAncestors(tenantId, id)).orElse(List.of());
        Optional<String> courseRole = memberships.activeRoleKey(tenantId, userId, courseId);
        return resolver.resolve(grants, ancestry, courseRole);
    }

    /** Своя транзакция: запись переживает последующий 404 и read-only транзакцию вызывающего кода (AC-1). */
    private void auditCrossTenantProbe(UUID tenantId, UUID userId, UUID courseId) {
        if (courses.existsInOtherTenant(tenantId, courseId)) {
            audit.recordIndependently(AuditRecord.of(tenantId, userId, "access.cross_tenant_denied", "course", courseId.toString()));
        }
    }

    private static List<RoleGrant> tenantLevel(List<RoleGrant> grants) {
        return grants.stream().filter(grant -> !RoleScope.CATEGORY.key().equals(grant.contextType())).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<TenantRole> tenantRoles(UUID tenantId, UUID userId) {
        return roles.grantsOf(tenantId, userId).stream()
                .map(grant -> TenantRole.find(grant.roleKey()))
                .flatMap(Optional::stream)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(TenantRole.class)));
    }

    @Override
    @Transactional
    public void replaceTenantRoles(UUID tenantId, UUID userId, Set<TenantRole> newRoles, UUID actorId) {
        Set<TenantRole> before = tenantRoles(tenantId, userId);
        roles.deleteTenantGrants(tenantId, userId);
        newRoles.stream()
                .filter(role -> role != TenantRole.CATEGORY_MANAGER && role != TenantRole.PLATFORM_ADMIN)
                .forEach(role -> roles.insertGrant(tenantId, userId, role.key(), RoleScope.TENANT.key(), null, actorId));
        audit.record(AuditRecord.of(tenantId, actorId, "role.tenant_roles_changed", "user", userId.toString())
                .withDiff(Map.of("before", keys(before), "after", keys(newRoles))));
    }

    @Override
    @Transactional
    public void assignCategoryManager(UUID tenantId, UUID userId, UUID categoryId, UUID actorId) {
        roles.insertGrant(tenantId, userId, TenantRole.CATEGORY_MANAGER.key(), RoleScope.CATEGORY.key(), categoryId, actorId);
        audit.record(AuditRecord.of(tenantId, actorId, "role.category_manager_assigned", "user", userId.toString())
                .withDiff(Map.of("categoryId", categoryId.toString())));
    }

    private static List<String> keys(Set<TenantRole> set) {
        return set.stream().map(TenantRole::key).sorted().toList();
    }
}
