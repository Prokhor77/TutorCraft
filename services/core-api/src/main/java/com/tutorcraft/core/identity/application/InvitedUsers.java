package com.tutorcraft.core.identity.application;

import com.tutorcraft.core.identity.application.UserRepository.NewUser;
import com.tutorcraft.core.identity.domain.UserAccount;
import com.tutorcraft.core.identity.domain.UserStatus;
import com.tutorcraft.core.org.OrgApi;
import com.tutorcraft.core.org.OrgApi.TenantInfo;
import com.tutorcraft.core.shared.domain.Ids;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Создание приглашённого пользователя без пароля (админка и CSV-импорт). Вызывать в транзакции. */
@Component
public class InvitedUsers {

    private final UserRepository users;
    private final OrgApi org;

    public InvitedUsers(UserRepository users, OrgApi org) {
        this.users = users;
        this.org = org;
    }

    /** @param email нормализованный email, свободный в tenant */
    public UserAccount create(UUID tenantId, String email, String firstName, String lastName) {
        TenantInfo tenant = org.require(tenantId);
        UUID id = Ids.newId();
        users.insert(new NewUser(id, tenantId, email, null, firstName, lastName, tenant.defaultTimezone(),
                tenant.defaultLocale(), UserStatus.INVITED, null, null));
        return users.findById(tenantId, id).orElseThrow(() -> new IllegalStateException("User was not persisted"));
    }
}
