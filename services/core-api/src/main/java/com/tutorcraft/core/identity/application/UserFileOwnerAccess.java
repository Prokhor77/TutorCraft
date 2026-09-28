package com.tutorcraft.core.identity.application;

import com.tutorcraft.core.files.spi.FileOwnerAccess;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Файлы-владельцы типа 'user' (аватары) видны любому пользователю того же tenant. */
@Component
class UserFileOwnerAccess implements FileOwnerAccess {

    private final UserRepository users;

    UserFileOwnerAccess(UserRepository users) {
        this.users = users;
    }

    @Override
    public String ownerType() {
        return MeService.AVATAR_OWNER_TYPE;
    }

    @Override
    public boolean canRead(UUID tenantId, UUID userId, UUID ownerId) {
        return users.findById(tenantId, userId).isPresent() && users.findById(tenantId, ownerId).isPresent();
    }
}
