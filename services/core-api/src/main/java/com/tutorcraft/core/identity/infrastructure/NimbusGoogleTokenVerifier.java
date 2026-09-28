package com.tutorcraft.core.identity.infrastructure;

import com.tutorcraft.core.identity.application.GoogleTokenVerifier;
import com.tutorcraft.core.identity.application.IdentityErrors;
import com.tutorcraft.core.shared.config.AppProperties;
import com.tutorcraft.core.shared.domain.UnauthorizedException;
import java.util.Collection;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * Проверка Google ID-token: подпись по JWKS Google (RS256), iss ∈ {accounts.google.com, https://accounts.google.com},
 * aud == GOOGLE_CLIENT_ID, exp/nbf. Ключи JWKS кэшируются Nimbus.
 */
@Component
class NimbusGoogleTokenVerifier implements GoogleTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(NimbusGoogleTokenVerifier.class);
    private static final String JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");
    private static final String EMAIL = "email";
    private static final String EMAIL_VERIFIED = "email_verified";
    private static final String GIVEN_NAME = "given_name";
    private static final String FAMILY_NAME = "family_name";

    private final JwtDecoder decoder;

    NimbusGoogleTokenVerifier(AppProperties properties) {
        AppProperties.OAuth oauth = properties.oauth();
        this.decoder = oauth.googleEnabled() ? buildDecoder(oauth.googleClientId()) : null;
    }

    @Override
    public GoogleIdentity verify(String idToken) {
        if (decoder == null || idToken == null || idToken.isBlank()) {
            throw invalid();
        }
        try {
            Jwt jwt = decoder.decode(idToken);
            return new GoogleIdentity(jwt.getSubject(), jwt.getClaimAsString(EMAIL), isTrue(jwt.getClaim(EMAIL_VERIFIED)),
                    jwt.getClaimAsString(GIVEN_NAME), jwt.getClaimAsString(FAMILY_NAME));
        } catch (JwtException e) {
            log.info("Google ID token rejected: {}", e.getClass().getSimpleName());
            throw invalid();
        }
    }

    private static JwtDecoder buildDecoder(String clientId) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(JWKS_URI).build();
        OAuth2TokenValidator<Jwt> issuer = new JwtClaimValidator<Object>(JwtClaimNames.ISS,
                iss -> iss != null && ISSUERS.contains(iss.toString()));
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<Object>(JwtClaimNames.AUD,
                aud -> aud instanceof Collection<?> values ? values.contains(clientId) : clientId.equals(String.valueOf(aud)));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(), issuer, audience));
        return decoder;
    }

    private static boolean isTrue(Object claim) {
        return claim instanceof Boolean flag ? flag : Boolean.parseBoolean(String.valueOf(claim));
    }

    private static UnauthorizedException invalid() {
        return new UnauthorizedException(IdentityErrors.OAUTH_INVALID, "Google token is invalid");
    }
}
