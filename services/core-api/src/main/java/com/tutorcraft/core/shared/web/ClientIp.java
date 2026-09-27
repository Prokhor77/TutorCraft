package com.tutorcraft.core.shared.web;

import jakarta.servlet.http.HttpServletRequest;

/** IP клиента. X-Forwarded-For обрабатывается Spring (server.forward-headers-strategy=framework). */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
