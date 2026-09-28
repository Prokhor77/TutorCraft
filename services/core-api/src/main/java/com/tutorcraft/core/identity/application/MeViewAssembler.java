package com.tutorcraft.core.identity.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.TenantRole;
import com.tutorcraft.core.files.FilesApi;
import com.tutorcraft.core.identity.domain.UserAccount;
import com.tutorcraft.core.org.OrgApi;
import com.tutorcraft.core.org.OrgApi.TenantInfo;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Сборка {@link MeView}: профиль + tenant с брендингом + роли уровня tenant. */
@Component
public class MeViewAssembler {

    private static final String READY = "ready";

    private final OrgApi org;
    private final AccessService access;
    private final FilesApi files;

    public MeViewAssembler(OrgApi org, AccessService access, FilesApi files) {
        this.org = org;
        this.access = access;
        this.files = files;
    }

    public MeView assemble(UserAccount user) {
        TenantInfo tenant = org.require(user.tenantId());
        MeView.Branding branding = new MeView.Branding(fileUrl(tenant.id(), tenant.logoFileId()), tenant.primaryColor());
        MeView.TenantView tenantView = new MeView.TenantView(tenant.id(), tenant.slug(), tenant.name(), branding);
        return new MeView(user.id(), user.email(), user.firstName(), user.lastName(), fileUrl(user.tenantId(), user.avatarFileId()),
                user.timezone(), user.locale(), tenantView, tenantRoleKeys(user), user.telegramLinked());
    }

    public List<String> tenantRoleKeys(UserAccount user) {
        return access.tenantRoles(user.tenantId(), user.id()).stream().map(TenantRole::key).sorted().toList();
    }

    private String fileUrl(UUID tenantId, UUID fileId) {
        if (fileId == null) {
            return null;
        }
        return files.find(tenantId, fileId)
                .filter(file -> READY.equals(file.status()))
                .map(files::downloadUrl)
                .orElse(null);
    }
}
