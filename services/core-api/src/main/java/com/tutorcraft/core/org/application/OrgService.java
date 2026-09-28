package com.tutorcraft.core.org.application;

import com.tutorcraft.core.access.spi.CategoryAncestry;
import com.tutorcraft.core.org.OrgApi;
import com.tutorcraft.core.org.domain.SlugGenerator;
import com.tutorcraft.core.shared.domain.Ids;
import com.tutorcraft.core.shared.domain.NotFoundException;
import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Реализация OrgApi и порта CategoryAncestry для модуля access. */
@Service
class OrgService implements OrgApi, CategoryAncestry {

    private static final int SLUG_SUFFIX_BOUND = 10_000;
    private static final int MAX_SLUG_ATTEMPTS = 20;

    private final TenantRepository tenants;
    private final CategoryRepository categories;
    private final SecureRandom random = new SecureRandom();

    OrgService(TenantRepository tenants, CategoryRepository categories) {
        this.tenants = tenants;
        this.categories = categories;
    }

    @Override
    @Transactional
    public TenantInfo createTenant(String name, String slugHint) {
        String slug = uniqueSlug(SlugGenerator.slugify(slugHint == null ? name : slugHint));
        UUID id = Ids.newId();
        tenants.insert(id, slug, name);
        return require(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TenantInfo> findBySlug(String slug) {
        return tenants.findBySlug(slug);
    }

    @Override
    @Transactional(readOnly = true)
    public TenantInfo require(UUID tenantId) {
        return tenants.findById(tenantId).orElseThrow(() -> new NotFoundException("tenant.not_found", "Tenant not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> embedWhitelist(UUID tenantId) {
        return tenants.embedWhitelist(tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> storageQuotaMb(UUID tenantId) {
        return tenants.storageQuotaMb(tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> selfAndAncestors(UUID tenantId, UUID categoryId) {
        return categories.selfAndAncestors(tenantId, categoryId);
    }

    private String uniqueSlug(String base) {
        if (!tenants.slugExists(base)) {
            return base;
        }
        for (int attempt = 0; attempt < MAX_SLUG_ATTEMPTS; attempt++) {
            String candidate = base + "-" + random.nextInt(SLUG_SUFFIX_BOUND);
            if (!tenants.slugExists(candidate)) {
                return candidate;
            }
        }
        return base + "-" + Ids.newId().toString().substring(0, 8);
    }
}
