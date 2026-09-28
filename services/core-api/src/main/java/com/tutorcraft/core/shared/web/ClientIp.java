package com.tutorcraft.core.shared.web;

import jakarta.servlet.http.HttpServletRequest;

/** IP клиента. X-Forwarded-For разбирает Tomcat RemoteIpValve (server.forward-headers-strategy=native). */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
