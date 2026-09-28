package com.tutorcraft.core.identity.web;

import com.tutorcraft.core.identity.application.IdentityErrors;
import com.tutorcraft.core.shared.config.AppProperties;
import com.tutorcraft.core.shared.domain.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/** Защита cookie-эндпоинтов от CSRF (NFR-SEC-04): заголовок Origin, если есть, должен совпадать с WEB_ORIGIN. */
@Component
class OriginGuard {

    private final String allowedOrigin;

    OriginGuard(AppProperties properties) {
        this.allowedOrigin = properties.webOrigin();
    }

    void check(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin != null && !origin.equals(allowedOrigin)) {
            throw new ForbiddenException(IdentityErrors.ORIGIN_MISMATCH, "Request origin is not allowed");
        }
    }
}
