package com.tutorcraft.core.integrations.domain;

/** Формат personal access token: {@code tcpat_<случайная часть>}; префикс отличает их от JWT и секретов других систем. */
public final class ApiTokenFormat {

    public static final String PREFIX = "tcpat_";

    private ApiTokenFormat() {
    }

    public static String of(String randomPart) {
        return PREFIX + randomPart;
    }

    public static boolean matches(String token) {
        return token != null && token.startsWith(PREFIX) && token.length() > PREFIX.length();
    }
}
