package com.tutorcraft.core.identity.web;

import com.tutorcraft.core.identity.application.AuthResult;
import com.tutorcraft.core.identity.application.MeView;

/** Контракт AuthResponse. Refresh-токен в тело не попадает — только в httpOnly cookie. */
record AuthResponse(String accessToken, long expiresIn, MeView user) {

    static AuthResponse of(AuthResult result) {
        return new AuthResponse(result.session().accessToken(), result.session().expiresInSeconds(), result.me());
    }
}
