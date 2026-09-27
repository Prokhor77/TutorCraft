package com.tutorcraft.core.shared.security;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

/** argon2id (FR-AUTH-01). Параметры — рекомендации OWASP 2024 для Spring Security 5.8+. */
@Component
public class PasswordHasher {

    private final Argon2PasswordEncoder encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String hash) {
        if (hash == null) {
            return false;
        }
        return encoder.matches(rawPassword, hash);
    }
}
