package com.tutorcraft.core.shared.web;

import com.tutorcraft.core.shared.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Кладёт tenantId/userId (только идентификаторы, без ПДн) в MDC для логов (NFR-OBS-01). */
@Component
class RequestContextInterceptor implements HandlerInterceptor {

    static final String TENANT_KEY = "tenantId";
    static final String USER_KEY = "userId";

    private final CurrentUserProvider currentUser;

    RequestContextInterceptor(CurrentUserProvider currentUser) {
        this.currentUser = currentUser;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        currentUser.find().ifPresent(user -> {
            MDC.put(TENANT_KEY, user.tenantId().toString());
            MDC.put(USER_KEY, user.userId().toString());
        });
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        MDC.remove(TENANT_KEY);
        MDC.remove(USER_KEY);
    }
}
