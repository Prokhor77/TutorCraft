package com.tutorcraft.core.integrations.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Скоупы personal access token (FR-INTEG-01): read — только безопасные HTTP-методы, write — любые. */
public enum ApiTokenScope {
    READ("read"), WRITE("write");

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final String key;

    ApiTokenScope(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Optional<ApiTokenScope> find(String key) {
        return Arrays.stream(values()).filter(scope -> scope.key.equals(key)).findFirst();
    }

    public static boolean permits(Set<ApiTokenScope> scopes, String httpMethod) {
        if (scopes.contains(WRITE)) {
            return true;
        }
        return scopes.contains(READ) && httpMethod != null && SAFE_METHODS.contains(httpMethod.toUpperCase(Locale.ROOT));
    }
}
