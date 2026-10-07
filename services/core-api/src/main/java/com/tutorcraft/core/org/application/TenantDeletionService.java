package com.tutorcraft.core.org.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.audit.AuditLog;
import com.tutorcraft.core.audit.AuditRecord;
import com.tutorcraft.core.files.FilesApi;
import com.tutorcraft.core.org.OrgApi;
import com.tutorcraft.core.org.OrgApi.TenantInfo;
import com.tutorcraft.core.shared.domain.BusinessRuleException;
import com.tutorcraft.core.shared.domain.NotFoundException;
import com.tutorcraft.core.shared.domain.ValidationException;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Полное удаление школы главным администратором (право platform.manage): все её пользователи — включая владельца,
 * на которого школа зарегистрирована, — курсы, оценки, файлы и настройки. PostgreSQL очищается в одной транзакции;
 * MongoDB и хранилище файлов — после её фиксации (там транзакций нет, повторное удаление безопасно).
 * Журнал аудита школы сохраняется, а само удаление записывается в журнал служебного tenant администратора.
 */
@Service
public class TenantDeletionService {

    static final String CONFIRMATION_FIELD = "confirmSlug";
    static final String TENANT_NOT_FOUND = "tenant.not_found";
    static final String TENANT_PROTECTED = "tenant.protected";
    static final String CONFIRMATION_MISMATCH = "confirmation_mismatch";

    private static final Logger log = LoggerFactory.getLogger(TenantDeletionService.class);

    private final TenantRepository tenants;
    private final TenantDataPurge purge;
    private final FilesApi files;
    private final AccessService access;
    private final CurrentUserProvider currentUser;
    private final AuditLog audit;

    TenantDeletionService(TenantRepository tenants, TenantDataPurge purge, FilesApi files, AccessService access,
                          CurrentUserProvider currentUser, AuditLog audit) {
        this.tenants = tenants;
        this.purge = purge;
        this.files = files;
        this.access = access;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    /** @param confirmSlug адрес школы, введённый администратором для подтверждения */
    @Transactional
    public void delete(UUID tenantId, String confirmSlug) {
        access.require(Permission.PLATFORM_MANAGE, AccessContext.tenant());
        CurrentUser actor = currentUser.require();
        TenantInfo tenant = tenants.findById(tenantId)
                .orElseThrow(() -> new NotFoundException(TENANT_NOT_FOUND, "Tenant not found"));
        if (OrgApi.PLATFORM_TENANT_SLUG.equals(tenant.slug()) || tenantId.equals(actor.homeTenantId())) {
            throw new BusinessRuleException(TENANT_PROTECTED, "The platform administration tenant cannot be deleted");
        }
        if (confirmSlug == null || !tenant.slug().equals(confirmSlug.strip())) {
            throw ValidationException.single(CONFIRMATION_FIELD, CONFIRMATION_MISMATCH, "Type the school address to confirm");
        }

        List<String> storagePrefixes = files.storagePrefixes(tenantId);
        Map<String, Integer> rows = purge.purgeRelational(tenantId);
        audit.record(AuditRecord.of(actor.homeTenantId(), actor.userId(), "tenant.deleted", "tenant", tenantId.toString())
                .withDiff(Map.of("slug", tenant.slug(), "name", tenant.name(),
                        "users", rows.getOrDefault("users", 0),
                        "courses", rows.getOrDefault("courses", 0),
                        "files", rows.getOrDefault("files", 0),
                        "rows", rows.values().stream().mapToInt(Integer::intValue).sum())));
        log.info("Tenant {} ({}) deleted by platform administrator {}", tenantId, tenant.slug(), actor.userId());

        afterCommit(() -> {
            purge.purgeDocuments(tenantId);
            files.deleteStorage(storagePrefixes);
        }, tenantId);
    }

    /** Ошибка очистки MongoDB/хранилища не отменяет удаление: остаются только недоступные «сироты», они в логе. */
    private static void afterCommit(Runnable cleanup, UUID tenantId) {
        Runnable guarded = () -> {
            try {
                cleanup.run();
            } catch (RuntimeException e) {
                log.error("Tenant {} deleted, but documents or files were not cleaned up", tenantId, e);
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            guarded.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                guarded.run();
            }
        });
    }
}
