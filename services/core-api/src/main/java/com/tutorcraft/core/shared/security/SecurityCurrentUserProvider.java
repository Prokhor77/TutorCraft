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

@Component
class SecurityCurrentUserProvider implements CurrentUserProvider {

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
        return new CurrentUser(userId, tenantId, tenantRoles);
    }
}
