package com.tutorcraft.core.identity.application;

import com.tutorcraft.core.identity.domain.UserAccount;
import java.time.Duration;

/** Результат входа: access JWT (в теле ответа) и refresh-токен (только в httpOnly cookie). */
public record IssuedSession(String accessToken, long expiresInSeconds, String refreshToken, Duration refreshTtl,
                            UserAccount user) {

    @Override
    public String toString() {
        return "IssuedSession[userId=" + user.id() + "]";
    }
}
