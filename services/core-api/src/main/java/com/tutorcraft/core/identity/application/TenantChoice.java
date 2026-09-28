package com.tutorcraft.core.identity.application;

import com.tutorcraft.core.identity.domain.UserAccount;
import com.tutorcraft.core.org.OrgApi;
import com.tutorcraft.core.org.OrgApi.TenantInfo;
import com.tutorcraft.core.shared.domain.ConflictException;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Выбор единственной учётной записи среди найденных в разных tenant. Если их несколько —
 * 409 {@code auth.tenant_required}; args.tenants содержит slug и название только этих tenant.
 */
@Component
public class TenantChoice {

    public static final String TENANTS_ARG = "tenants";
    private static final String SLUG = "slug";
    private static final String NAME = "name";

    private final OrgApi org;

    public TenantChoice(OrgApi org) {
        this.org = org;
    }

    /** @param accounts непустой список кандидатов */
    public UserAccount single(List<UserAccount> accounts) {
        if (accounts.isEmpty()) {
            throw new IllegalArgumentException("accounts must not be empty");
        }
        if (accounts.size() == 1) {
            return accounts.get(0);
        }
        List<Map<String, String>> tenants = accounts.stream()
                .map(account -> org.require(account.tenantId()))
                .map(TenantChoice::describe)
                .toList();
        throw new ConflictException(IdentityErrors.TENANT_REQUIRED, "Choose a school to sign in", Map.of(TENANTS_ARG, tenants));
    }

    private static Map<String, String> describe(TenantInfo tenant) {
        return Map.of(SLUG, tenant.slug(), NAME, tenant.name());
    }
}
