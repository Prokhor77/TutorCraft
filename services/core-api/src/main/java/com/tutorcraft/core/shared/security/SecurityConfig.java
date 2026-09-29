package com.tutorcraft.core.shared.security;

import com.tutorcraft.core.shared.api.ProblemWriter;
import com.tutorcraft.core.shared.config.AppProperties;
import com.tutorcraft.core.shared.web.RequestCorrelation;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
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
    /** Шлюз файлов локального хранилища (files.web.StorageController): доступ по подписи ссылки, не по JWT. */
    private static final String STORAGE_PATHS = "/storage/**";
    /**
     * Пользовательские файлы отдаются с origin приложения, поэтому скрипты в них запрещены (SVG, HTML уходят как
     * attachment — NFR-SEC-05, это второй рубеж). frame-ancestors 'self' — чтобы работал предпросмотр PDF в {@code <object>}.
     */
    private static final String STORAGE_CSP = "default-src 'none'; img-src 'self' data:; media-src 'self'; "
            + "style-src 'unsafe-inline'; frame-ancestors 'self'";

    /** Отдельная цепочка раньше основной: без JWT, CORS и сессий, со своими заголовками для содержимого файлов. */
    @Bean
    @Order(1)
    SecurityFilterChain storageFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(STORAGE_PATHS)
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(AbstractHttpConfigurer::disable)
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(STORAGE_CSP))
                .frameOptions(frame -> frame.sameOrigin())
                // Cache-Control выставляет StorageController: сегменты HLS кешируются, личные файлы — нет.
                .cacheControl(cache -> cache.disable()))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @Order(2)
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
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "If-Match", "Accept-Language",
                CurrentUser.TENANT_OVERRIDE_HEADER, RequestCorrelation.CLIENT_PAGE_HEADER, RequestCorrelation.CLIENT_SESSION_HEADER));
        config.setExposedHeaders(List.of("ETag", "X-Unread-Count", "RateLimit-Limit", "RateLimit-Remaining", "RateLimit-Reset",
                RequestCorrelation.REQUEST_ID_HEADER));
        config.setAllowCredentials(true);
        config.setMaxAge(CORS_MAX_AGE_SECONDS);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
