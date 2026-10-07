package com.tutorcraft.core.org.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.audit.AuditLog;
import com.tutorcraft.core.audit.AuditRecord;
import com.tutorcraft.core.files.FilesApi;
import com.tutorcraft.core.org.OrgApi;
import com.tutorcraft.core.org.OrgApi.PasswordPolicy;
import com.tutorcraft.core.org.OrgApi.TenantInfo;
import com.tutorcraft.core.shared.domain.BusinessRuleException;
import com.tutorcraft.core.shared.domain.ValidationException;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TenantDeletionServiceTest {

    private static final UUID ADMIN = UUID.randomUUID();
    private static final UUID PLATFORM_TENANT = UUID.randomUUID();
    private static final UUID SCHOOL = UUID.randomUUID();
    private static final List<String> PREFIXES = List.of("t/" + SCHOOL + "/");

    private final TenantRepository tenants = mock(TenantRepository.class);
    private final TenantDataPurge purge = mock(TenantDataPurge.class);
    private final FilesApi files = mock(FilesApi.class);
    private final AccessService access = mock(AccessService.class);
    private final CurrentUserProvider currentUser = mock(CurrentUserProvider.class);
    private final AuditLog audit = mock(AuditLog.class);
    private TenantDeletionService service;

    @BeforeEach
    void setUp() {
        when(currentUser.require()).thenReturn(new CurrentUser(ADMIN, SCHOOL, Set.of("platform_admin"), PLATFORM_TENANT));
        when(tenants.findById(SCHOOL)).thenReturn(Optional.of(tenant(SCHOOL, "math-school")));
        when(files.storagePrefixes(SCHOOL)).thenReturn(PREFIXES);
        Map<String, Integer> rows = new LinkedHashMap<>();
        rows.put("courses", 2);
        rows.put("users", 3);
        rows.put("tenants", 1);
        when(purge.purgeRelational(SCHOOL)).thenReturn(rows);
        service = new TenantDeletionService(tenants, purge, files, access, currentUser, audit);
    }

    @Test
    void deletesEverythingAndRecordsItInPlatformAudit() {
        service.delete(SCHOOL, " math-school ");

        verify(purge).purgeRelational(SCHOOL);
        // Без активной транзакции очистка MongoDB и хранилища выполняется сразу.
        verify(purge).purgeDocuments(SCHOOL);
        verify(files).deleteStorage(PREFIXES);
        ArgumentCaptor<AuditRecord> record = ArgumentCaptor.forClass(AuditRecord.class);
        verify(audit).record(record.capture());
        assertThat(record.getValue().tenantId()).isEqualTo(PLATFORM_TENANT);
        assertThat(record.getValue().action()).isEqualTo("tenant.deleted");
        assertThat(record.getValue().diff()).containsEntry("users", 3).containsEntry("rows", 6);
    }

    @Test
    void wrongConfirmationDeletesNothing() {
        assertThatThrownBy(() -> service.delete(SCHOOL, "other-school")).isInstanceOf(ValidationException.class);

        verify(purge, never()).purgeRelational(any());
        verify(files, never()).deleteStorage(any());
    }

    @Test
    void platformTenantIsProtected() {
        when(tenants.findById(PLATFORM_TENANT)).thenReturn(Optional.of(tenant(PLATFORM_TENANT, OrgApi.PLATFORM_TENANT_SLUG)));

        assertThatThrownBy(() -> service.delete(PLATFORM_TENANT, OrgApi.PLATFORM_TENANT_SLUG))
                .isInstanceOf(BusinessRuleException.class);
        verify(purge, never()).purgeRelational(any());
    }

    private static TenantInfo tenant(UUID id, String slug) {
        return new TenantInfo(id, slug, "School", "active", "ru", "Europe/Moscow", null, null,
                new PasswordPolicy(10, true, true));
    }
}
