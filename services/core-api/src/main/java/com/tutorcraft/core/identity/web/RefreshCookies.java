package com.tutorcraft.core.identity.web;

import com.tutorcraft.core.shared.config.AppProperties;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** Cookie refresh-токена (ADR-003): httpOnly, SameSite=Strict, Path=/api/v1/auth, Secure по конфигурации. */
@Component
class RefreshCookies {

    static final String NAME = "tc_refresh";
    private static final String PATH = "/api/v1/auth";
    private static final String SAME_SITE = "Strict";

    private final boolean secure;

    RefreshCookies(AppProperties properties) {
        this.secure = properties.security().cookieSecure();
    }

    String issue(String token, Duration ttl) {
        return base(token).maxAge(ttl).build().toString();
    }

    String clear() {
        return base("").maxAge(Duration.ZERO).build().toString();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value).httpOnly(true).secure(secure).sameSite(SAME_SITE).path(PATH);
    }
}
