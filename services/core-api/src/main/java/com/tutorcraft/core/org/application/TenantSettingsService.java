package com.tutorcraft.core.org.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.audit.AuditLog;
import com.tutorcraft.core.audit.AuditRecord;
import com.tutorcraft.core.org.OrgApi.PasswordPolicy;
import com.tutorcraft.core.org.application.TenantRepository.TenantSettingsUpdate;
import com.tutorcraft.core.org.application.TenantRepository.TenantSettingsView;
import com.tutorcraft.core.shared.api.IfMatch;
import com.tutorcraft.core.shared.domain.ConflictException;
import com.tutorcraft.core.shared.domain.NotFoundException;
import com.tutorcraft.core.shared.domain.Validator;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Настройки tenant и брендинг (FR-ADMIN-01). */
@Service
public class TenantSettingsService {

    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final Pattern DOMAIN = Pattern.compile("^[a-z0-9.-]+\\.[a-z]{2,}$");
    private static final Set<String> LOCALES = Set.of("ru", "en", "uz");
    private static final int MAX_NAME = 200;
    private static final int MAX_WHITELIST = 100;

    private final TenantRepository tenants;
    private final AccessService access;
    private final CurrentUserProvider currentUser;
    private final AuditLog audit;

    public TenantSettingsService(TenantRepository tenants, AccessService access, CurrentUserProvider currentUser, AuditLog audit) {
        this.tenants = tenants;
        this.access = access;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public TenantSettingsView get() {
        CurrentUser user = currentUser.require();
        return load(user);
    }

    @Transactional
    public TenantSettingsView update(TenantSettingsUpdate update, long expectedVersion) {
        CurrentUser user = currentUser.require();
        access.require(Permission.TENANT_MANAGE, AccessContext.tenant());
        validate(update);
        TenantSettingsView before = load(user);
        IfMatch.check(expectedVersion, before.version());
        if (!tenants.updateSettings(user.tenantId(), expectedVersion, update)) {
            throw new ConflictException(IfMatch.VERSION_CONFLICT_CODE, "Tenant settings were modified");
        }
        audit.record(AuditRecord.of(user.tenantId(), user.userId(), "tenant.settings_updated", "tenant",
                user.tenantId().toString()).withDiff(Map.of("name", update.name(), "locale", update.defaultLocale())));
        return load(user);
    }

    private TenantSettingsView load(CurrentUser user) {
        return tenants.settings(user.tenantId()).orElseThrow(() -> new NotFoundException("tenant.not_found", "Tenant not found"));
    }

    private static void validate(TenantSettingsUpdate update) {
        PasswordPolicy policy = update.passwordPolicy();
        List<String> whitelist = update.embedWhitelist() == null ? List.of() : update.embedWhitelist();
        new Validator()
            .notBlank(update.name(), "name")
            .maxLength(update.name(), MAX_NAME, "name")
            .check(update.primaryColor() == null || HEX_COLOR.matcher(update.primaryColor()).matches(), "primaryColor", "invalid", "Use #RRGGBB")
            .check(LOCALES.contains(update.defaultLocale()), "defaultLocale", "invalid", "Supported: ru, en, uz")
            .check(isZone(update.defaultTimezone()), "defaultTimezone", "invalid", "Unknown time zone")
            .check(policy != null && policy.minLength() >= PasswordPolicy.ABSOLUTE_MIN_LENGTH && policy.minLength() <= PasswordPolicy.MAX_LENGTH,
                    "passwordPolicy.minLength", "out_of_range", "Between 8 and 128")
            .check(whitelist.size() <= MAX_WHITELIST && whitelist.stream().allMatch(d -> DOMAIN.matcher(d).matches()),
                    "embedWhitelist", "invalid", "Domains only, e.g. www.youtube.com")
            .throwIfInvalid();
    }

    private static boolean isZone(String zone) {
        try {
            ZoneId.of(zone);
            return true;
        } catch (DateTimeException | NullPointerException e) {
            return false;
        }
    }
}
