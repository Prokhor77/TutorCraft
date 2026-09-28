package com.tutorcraft.core.identity.application;

import com.tutorcraft.core.identity.UsersApi;
import com.tutorcraft.core.identity.domain.EmailAddress;
import com.tutorcraft.core.identity.domain.UserAccount;
import com.tutorcraft.core.identity.domain.UserStatus;
import com.tutorcraft.core.shared.domain.NotFoundException;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Реализация UsersApi. Зависит только от репозитория (правило против циклов бинов). */
@Component
class UsersApiAdapter implements UsersApi {

    private final UserRepository users;

    UsersApiAdapter(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserRef> find(UUID tenantId, UUID userId) {
        return users.findById(tenantId, userId).map(UsersApiAdapter::toRef);
    }

    @Override
    @Transactional(readOnly = true)
    public UserRef require(UUID tenantId, UUID userId) {
        return find(tenantId, userId).orElseThrow(() -> new NotFoundException(IdentityErrors.USER_NOT_FOUND, "User not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, UserRef> findAll(UUID tenantId, Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return users.findAll(tenantId, userIds).stream()
                .map(UsersApiAdapter::toRef)
                .collect(Collectors.toMap(UserRef::id, Function.identity()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserRef> findByEmail(UUID tenantId, String email) {
        if (!EmailAddress.isValid(email)) {
            return Optional.empty();
        }
        return users.findByEmail(tenantId, EmailAddress.normalize(email)).map(UsersApiAdapter::toRef);
    }

    private static UserRef toRef(UserAccount user) {
        return new UserRef(user.id(), user.tenantId(), user.email(), user.firstName(), user.lastName(), user.locale(),
                user.timezone(), effectiveStatus(user).key(), user.telegramChatId(), user.avatarFileId());
    }

    /** Для других модулей блокировка платформой неотличима от приостановки: пользователь не действует. */
    private static UserStatus effectiveStatus(UserAccount user) {
        return user.isPlatformBlocked() ? UserStatus.SUSPENDED : user.status();
    }
}
