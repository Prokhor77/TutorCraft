package com.tutorcraft.core.shared.security;

import com.tutorcraft.core.shared.api.ProblemWriter;
import com.tutorcraft.core.shared.config.AppProperties;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless-безопасность: Bearer JWT для API. CSRF не нужен — cookie используется только
 * для refresh (SameSite=Strict + проверка Origin в AuthController), NFR-SEC-04.
 */
@Configuration
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
        "/api/v1/auth/**",
        "/api/v1/public/**",
        "/api/v1/billing/webhooks/**",
        "/api/v1/calendar/ical/**",
        "/api/v1/openapi.json",
        "/api/docs/**",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/health/**",
        "/actuator/health/**",
        "/actuator/prometheus",
        "/error"
    };
    private static final long CORS_MAX_AGE_SECONDS = 3600;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService, ProblemWriter problemWriter,
                                            ObjectProvider<PersonalAccessTokenFilter> patFilters) throws Exception {
        PersonalAccessTokenFilter patFilter = patFilters.getIfAvailable();
        if (patFilter != null) {
            http.addFilterBefore(patFilter, BearerTokenAuthenticationFilter.class);
        }
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> { })
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                .frameOptions(frame -> frame.deny()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_PATHS).permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth
                .bearerTokenResolver(jwtOnlyResolver(patFilter))
                .jwt(jwt -> jwt.decoder(jwtService.decoder()))
                .authenticationEntryPoint((request, response, ex) ->
                    problemWriter.write(request, response, HttpStatus.UNAUTHORIZED, "auth.required")))
            .exceptionHandling(ex -> ex.accessDeniedHandler((request, response, denied) ->
                problemWriter.write(request, response, HttpStatus.FORBIDDEN, "access.denied")));
        return http.build();
    }

    /** PAT (FR-INTEG-01) обрабатывает PersonalAccessTokenFilter; JWT-декодеру они не передаются. */
    private static BearerTokenResolver jwtOnlyResolver(PersonalAccessTokenFilter patFilter) {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        return request -> {
            String token = delegate.resolve(request);
            return token != null && patFilter != null && patFilter.supports(token) ? null : token;
        };
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(properties.webOrigin()));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "If-Match", "Accept-Language"));
        config.setExposedHeaders(List.of("ETag", "X-Unread-Count", "RateLimit-Limit", "RateLimit-Remaining", "RateLimit-Reset"));
        config.setAllowCredentials(true);
        config.setMaxAge(CORS_MAX_AGE_SECONDS);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
