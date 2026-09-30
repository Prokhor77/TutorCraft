package com.tutorcraft.core.identity.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tutorcraft.core.identity.application.AuthResult;
import com.tutorcraft.core.identity.application.AuthService;
import com.tutorcraft.core.identity.application.AuthService.RegisterCommand;
import com.tutorcraft.core.identity.application.InvitationService;
import com.tutorcraft.core.identity.application.InvitationService.AcceptCommand;
import com.tutorcraft.core.identity.application.LoginThrottledException;
import com.tutorcraft.core.identity.application.OAuthService;
import com.tutorcraft.core.identity.application.OAuthService.ProvidersView;
import com.tutorcraft.core.identity.application.PasswordLoginService;
import com.tutorcraft.core.identity.application.PasswordLoginService.LoginCommand;
import com.tutorcraft.core.identity.application.PasswordService;
import com.tutorcraft.core.shared.api.ProblemFactory;
import com.tutorcraft.core.shared.web.ClientIp;
import com.tutorcraft.core.shared.web.RequestCorrelation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Аутентификация (контракт §1). */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private static final int MAX_PASSWORD = 128;
    private static final int MAX_TEXT = 254;
    private static final int MAX_NAME = 100;
    private static final int MAX_SLUG = 100;
    private static final int MAX_SCHOOL = 200;

    private final AuthService auth;
    private final PasswordLoginService passwordLogin;
    private final OAuthService oauth;
    private final PasswordService passwords;
    private final InvitationService invitations;
    private final RefreshCookies cookies;
    private final OriginGuard originGuard;
    private final ProblemFactory problems;

    AuthController(AuthService auth, PasswordLoginService passwordLogin, OAuthService oauth, PasswordService passwords,
                   InvitationService invitations, RefreshCookies cookies, OriginGuard originGuard, ProblemFactory problems) {
        this.auth = auth;
        this.passwordLogin = passwordLogin;
        this.oauth = oauth;
        this.passwords = passwords;
        this.invitations = invitations;
        this.cookies = cookies;
        this.originGuard = originGuard;
        this.problems = problems;
    }

    @PostMapping("/register")
    ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return withCookie(auth.register(new RegisterCommand(request.email(), request.password(), request.firstName(),
                request.lastName(), request.schoolName(), Boolean.TRUE.equals(request.acceptTerms()))));
    }

    @PostMapping("/login")
    ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        LoginCommand command = new LoginCommand(request.email(), request.password(), request.tenantSlug());
        return withCookie(passwordLogin.login(command, ClientIp.of(http)));
    }

    @PostMapping("/oauth/google")
    ResponseEntity<AuthResponse> google(@Valid @RequestBody GoogleRequest request) {
        return withCookie(oauth.google(request.idToken(), request.tenantSlug()));
    }

    @PostMapping("/oauth/telegram")
    ResponseEntity<AuthResponse> telegram(@Valid @RequestBody TelegramRequest request) {
        return withCookie(oauth.telegram(request.fields(), request.tenantSlug()));
    }

    @PostMapping("/refresh")
    ResponseEntity<AuthResponse> refresh(@CookieValue(name = RefreshCookies.NAME, required = false) String token,
                                         HttpServletRequest http) {
        originGuard.check(http);
        return withCookie(auth.refresh(token));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@CookieValue(name = RefreshCookies.NAME, required = false) String token,
                                HttpServletRequest http) {
        originGuard.check(http);
        auth.logout(token);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear()).build();
    }

    @PostMapping("/logout-all")
    ResponseEntity<Void> logoutAll() {
        auth.logoutAll();
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear()).build();
    }

    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwords.requestReset(request.email(), request.tenantSlug());
    }

    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwords.reset(request.token(), request.newPassword());
    }

    @PostMapping("/invitations/accept")
    ResponseEntity<AuthResponse> acceptInvitation(@Valid @RequestBody AcceptInvitationRequest request) {
        return withCookie(invitations.accept(new AcceptCommand(request.token(), request.password(), request.firstName(),
                request.lastName(), Boolean.TRUE.equals(request.acceptTerms()))));
    }

    @GetMapping("/providers")
    ProvidersView providers() {
        return oauth.providers();
    }

    @ExceptionHandler(LoginThrottledException.class)
    ResponseEntity<ProblemDetail> throttled(LoginThrottledException ex, Locale locale) {
        ProblemDetail problem = problems.create(HttpStatus.TOO_MANY_REQUESTS, ex.code(), ex.args(), List.of(), locale);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfterSeconds()))
                .body(problem);
    }

    private ResponseEntity<AuthResponse> withCookie(AuthResult result) {
        RequestCorrelation.rememberActor(result.me().id(), result.me().tenant().id());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.issue(result.session().refreshToken(), result.session().refreshTtl()))
                .body(AuthResponse.of(result));
    }

    record RegisterRequest(@NotBlank @Size(max = MAX_TEXT) String email, @NotBlank @Size(max = MAX_PASSWORD) String password,
                           @NotBlank @Size(max = MAX_NAME) String firstName, @NotBlank @Size(max = MAX_NAME) String lastName,
                           @Size(max = MAX_SCHOOL) String schoolName, Boolean acceptTerms) {

        @Override
        public String toString() {
            return "RegisterRequest[***]";
        }
    }

    record LoginRequest(@NotBlank @Size(max = MAX_TEXT) String email, @NotBlank @Size(max = MAX_PASSWORD) String password,
                        @Size(max = MAX_SLUG) String tenantSlug) {

        @Override
        public String toString() {
            return "LoginRequest[***]";
        }
    }

    record GoogleRequest(@NotBlank String idToken, @Size(max = MAX_SLUG) String tenantSlug) {
    }

    /** Данные Telegram Login Widget в исходных именах полей. */
    record TelegramRequest(@NotNull Long id,
                           @JsonProperty("first_name") String firstName,
                           @JsonProperty("last_name") String lastName,
                           String username,
                           @JsonProperty("photo_url") String photoUrl,
                           @NotNull @JsonProperty("auth_date") Long authDate,
                           @NotBlank String hash,
                           @Size(max = MAX_SLUG) String tenantSlug) {

        /** Все непустые поля виджета (кроме tenantSlug) — вход для проверки подписи. */
        Map<String, String> fields() {
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("id", String.valueOf(id));
            putIfPresent(fields, "first_name", firstName);
            putIfPresent(fields, "last_name", lastName);
            putIfPresent(fields, "username", username);
            putIfPresent(fields, "photo_url", photoUrl);
            fields.put("auth_date", String.valueOf(authDate));
            fields.put("hash", hash);
            return fields;
        }

        private static void putIfPresent(Map<String, String> fields, String key, String value) {
            if (value != null) {
                fields.put(key, value);
            }
        }
    }

    record ForgotPasswordRequest(@NotBlank @Size(max = MAX_TEXT) String email, @Size(max = MAX_SLUG) String tenantSlug) {
    }

    record ResetPasswordRequest(@NotBlank String token, @NotBlank @Size(max = MAX_PASSWORD) String newPassword) {

        @Override
        public String toString() {
            return "ResetPasswordRequest[***]";
        }
    }

    record AcceptInvitationRequest(@NotBlank String token, @NotBlank @Size(max = MAX_PASSWORD) String password,
                                   @NotBlank @Size(max = MAX_NAME) String firstName, @NotBlank @Size(max = MAX_NAME) String lastName,
                                   Boolean acceptTerms) {

        @Override
        public String toString() {
            return "AcceptInvitationRequest[***]";
        }
    }
}
