package com.tutorcraft.core.shared.security;

import com.tutorcraft.core.shared.domain.UnauthorizedException;
import java.util.Optional;

/** Источник текущего пользователя. Слой application получает его через этот порт (тестируемость). */
public interface CurrentUserProvider {

    Optional<CurrentUser> find();

    default CurrentUser require() {
        return find().orElseThrow(() -> new UnauthorizedException("auth.required", "Authentication required"));
    }
}
