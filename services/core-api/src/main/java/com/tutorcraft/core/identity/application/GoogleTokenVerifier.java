package com.tutorcraft.core.identity.application;

/** Порт проверки Google ID-токена (OIDC, ADR-002). Реализация — Nimbus + JWKS Google. */
public interface GoogleTokenVerifier {

    /** @throws com.tutorcraft.core.shared.domain.UnauthorizedException code {@code auth.oauth_invalid} */
    GoogleIdentity verify(String idToken);

    record GoogleIdentity(String subject, String email, boolean emailVerified, String givenName, String familyName) {
    }
}
