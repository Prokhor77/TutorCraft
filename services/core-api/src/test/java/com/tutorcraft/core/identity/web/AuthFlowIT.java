package com.tutorcraft.core.identity.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.tutorcraft.core.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** Регистрация репетитора → вход → профиль → ротация refresh (FR-AUTH-01/04, ADR-003). */
class AuthFlowIT extends IntegrationTest {

    private static final String PASSWORD = "Str0ngPassw0rd";
    private static final String REFRESH_COOKIE = "tc_refresh";

    @Autowired
    private ObjectMapper json;

    @Test
    void registerLoginAndReadProfile() throws Exception {
        String email = "tutor-" + UUID.randomUUID() + "@example.com";

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                        Map.of("email", email, "password", PASSWORD, "firstName", "Анна", "lastName", "Петрова",
                                "schoolName", "Школа Анны", "acceptTerms", true))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.user.tenantRoles[0]").value("tenant_admin"))
            .andExpect(cookie().httpOnly(REFRESH_COOKIE, true))
            .andExpect(cookie().path(REFRESH_COOKIE, "/api/v1/auth"));

        MvcResult login = login(email, PASSWORD).andExpect(status().isOk()).andReturn();
        String accessToken = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");

        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(email))
            .andExpect(jsonPath("$.firstName").value("Анна"))
            .andExpect(jsonPath("$.tenant.name").value("Школа Анны"))
            .andExpect(jsonPath("$.telegramLinked").value(false));
    }

    @Test
    void refreshRotatesTokenAndDetectsReuse() throws Exception {
        String email = register();
        Cookie first = login(email, PASSWORD).andReturn().getResponse().getCookie(REFRESH_COOKIE);

        MvcResult rotated = mvc.perform(post("/api/v1/auth/refresh").cookie(first))
            .andExpect(status().isOk())
            .andReturn();
        Cookie second = rotated.getResponse().getCookie(REFRESH_COOKIE);

        mvc.perform(post("/api/v1/auth/refresh").cookie(first)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").cookie(second)).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshFromForeignOriginIsForbidden() throws Exception {
        String email = register();
        Cookie refresh = login(email, PASSWORD).andReturn().getResponse().getCookie(REFRESH_COOKIE);

        mvc.perform(post("/api/v1/auth/refresh").cookie(refresh).header(HttpHeaders.ORIGIN, "https://evil.example"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("auth.origin_mismatch"));
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameError() throws Exception {
        String email = register();

        login(email, "WrongPassw0rd").andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("auth.invalid_credentials"));
        login("nobody-" + UUID.randomUUID() + "@example.com", PASSWORD).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("auth.invalid_credentials"));
    }

    @Test
    void weakPasswordIsRejectedByTenantPolicy() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                        Map.of("email", "weak-" + UUID.randomUUID() + "@example.com", "password", "short",
                                "firstName", "A", "lastName", "B", "acceptTerms", true))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    @Test
    void registrationWithoutAcceptedTermsIsRejected() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                        Map.of("email", "no-consent-" + UUID.randomUUID() + "@example.com", "password", PASSWORD,
                                "firstName", "A", "lastName", "B"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("acceptTerms"));
    }

    private String register() throws Exception {
        String email = "tutor-" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                        Map.of("email", email, "password", PASSWORD, "firstName", "Иван", "lastName", "Иванов",
                                "acceptTerms", true))))
            .andExpect(status().isOk());
        return email;
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", password))));
    }
}
