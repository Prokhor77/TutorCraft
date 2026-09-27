package com.tutorcraft.core.shared.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.tutorcraft.core.shared.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

/** Выпуск и проверка access JWT (ADR-003). */
@Component
public class JwtService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final AppProperties.Security settings;
    private final Clock clock;

    public JwtService(AppProperties properties, Clock clock) {
        this.settings = properties.security();
        this.clock = clock;
        SecretKey key = new SecretKeySpec(settings.jwtSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    public IssuedToken issueAccessToken(UUID userId, UUID tenantId, List<String> tenantRoles) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(settings.accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtClaims.ISSUER)
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(JwtClaims.TENANT_ID, tenantId.toString())
                .claim(JwtClaims.TENANT_ROLES, tenantRoles)
                .claim(JwtClaims.TOKEN_TYPE, JwtClaims.ACCESS_TYPE)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, settings.accessTokenTtl().toSeconds());
    }

    public JwtDecoder decoder() {
        return decoder;
    }

    public record IssuedToken(String value, long expiresInSeconds) {
    }
}
