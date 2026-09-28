package com.tutorcraft.core.shared.security;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
class SecurityCurrentUserProvider implements CurrentUserProvider {

    private static final String PLATFORM_ADMIN_ROLE = "platform_admin";

    @Override
    public Optional<CurrentUser> find() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return Optional.empty();
        }
        return Optional.of(toCurrentUser(jwt));
    }

    private static CurrentUser toCurrentUser(Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        UUID tenantId = UUID.fromString(jwt.getClaimAsString(JwtClaims.TENANT_ID));
        List<String> roles = jwt.getClaimAsStringList(JwtClaims.TENANT_ROLES);
        Set<String> tenantRoles = roles == null ? Set.of() : Set.copyOf(new HashSet<>(roles));
        UUID effectiveTenant = tenantRoles.contains(PLATFORM_ADMIN_ROLE) ? tenantOverride().orElse(tenantId) : tenantId;
        return new CurrentUser(userId, effectiveTenant, tenantRoles, tenantId);
    }

    /**
     * Школа, выбранная главным администратором в админке. Заголовок учитывается только при роли platform_admin
     * в токене; саму роль AccessService дополнительно проверяет по БД.
     */
    private static Optional<UUID> tenantOverride() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return Optional.empty();
        }
        String header = attributes.getRequest().getHeader(CurrentUser.TENANT_OVERRIDE_HEADER);
        if (header == null || header.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(header.trim()));
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }
}
