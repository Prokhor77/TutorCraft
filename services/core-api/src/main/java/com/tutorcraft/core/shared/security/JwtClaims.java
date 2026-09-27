package com.tutorcraft.core.shared.security;

/** Имена claim'ов access-токена. Контракт общий с notifier (Go). */
public final class JwtClaims {

    public static final String TENANT_ID = "tid";
    public static final String TENANT_ROLES = "troles";
    public static final String TOKEN_TYPE = "typ";
    public static final String ACCESS_TYPE = "access";
    public static final String ISSUER = "tutorcraft-core";

    private JwtClaims() {
    }
}
