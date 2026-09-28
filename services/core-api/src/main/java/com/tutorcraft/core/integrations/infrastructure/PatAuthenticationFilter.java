package com.tutorcraft.core.integrations.infrastructure;

import com.tutorcraft.core.integrations.application.ApiTokenAuthenticator;
import com.tutorcraft.core.integrations.application.ApiTokenAuthenticator.TokenPrincipal;
import com.tutorcraft.core.integrations.application.IntegrationErrors;
import com.tutorcraft.core.shared.api.ProblemWriter;
import com.tutorcraft.core.shared.security.JwtClaims;
import com.tutorcraft.core.shared.security.PersonalAccessTokenFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Аутентификация по {@code Authorization: Bearer tcpat_...} (FR-INTEG-01). Строит синтетический Jwt с теми же
 * claim'ами, что и access-токен (sub, tid), поэтому CurrentUserProvider и AccessService работают без изменений.
 * Токен только со скоупом read допускает лишь GET/HEAD/OPTIONS.
 */
@Component
class PatAuthenticationFilter extends OncePerRequestFilter implements PersonalAccessTokenFilter {

    private static final Logger log = LoggerFactory.getLogger(PatAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String TOKEN_VALUE_PREFIX = "pat:";
    private static final String TOKEN_TYPE = "pat";
    private static final String SYNTHETIC_ALGORITHM_HEADER = "alg";
    private static final String SYNTHETIC_ALGORITHM = "none";
    private static final String SCOPE_AUTHORITY_PREFIX = "SCOPE_";
    private static final Duration PRINCIPAL_LIFETIME = Duration.ofMinutes(5);

    private final ApiTokenAuthenticator authenticator;
    private final ProblemWriter problemWriter;
    private final Clock clock;

    PatAuthenticationFilter(ApiTokenAuthenticator authenticator, ProblemWriter problemWriter, Clock clock) {
        this.authenticator = authenticator;
        this.problemWriter = problemWriter;
        this.clock = clock;
    }

    @Override
    public boolean supports(String bearerToken) {
        return authenticator.supports(bearerToken);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = bearerToken(request);
        if (token == null || !supports(token)) {
            chain.doFilter(request, response);
            return;
        }
        Optional<TokenPrincipal> principal = authenticator.authenticate(token);
        if (principal.isEmpty()) {
            log.info("Rejected invalid API token");
            problemWriter.write(request, response, HttpStatus.UNAUTHORIZED, IntegrationErrors.INVALID_TOKEN);
            return;
        }
        if (!principal.get().permits(request.getMethod())) {
            problemWriter.write(request, response, HttpStatus.FORBIDDEN, IntegrationErrors.READ_ONLY_TOKEN);
            return;
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication(principal.get()));
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }

    private JwtAuthenticationToken authentication(TokenPrincipal principal) {
        Instant now = clock.instant();
        Jwt jwt = Jwt.withTokenValue(TOKEN_VALUE_PREFIX + principal.tokenId())
                .header(SYNTHETIC_ALGORITHM_HEADER, SYNTHETIC_ALGORITHM)
                .issuer(JwtClaims.ISSUER)
                .subject(principal.userId().toString())
                .claim(JwtClaims.TENANT_ID, principal.tenantId().toString())
                .claim(JwtClaims.TENANT_ROLES, List.of())
                .claim(JwtClaims.TOKEN_TYPE, TOKEN_TYPE)
                .issuedAt(now)
                .expiresAt(now.plus(PRINCIPAL_LIFETIME))
                .build();
        List<GrantedAuthority> authorities = principal.scopes().stream()
                .<GrantedAuthority>map(scope -> new SimpleGrantedAuthority(SCOPE_AUTHORITY_PREFIX + scope.key()))
                .toList();
        return new JwtAuthenticationToken(jwt, authorities, principal.userId().toString());
    }

    private static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        return header.substring(BEARER_PREFIX.length()).trim();
    }
}
