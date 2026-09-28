package com.tutorcraft.core.identity.web;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tutorcraft.core.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** Администрирование пользователей: разрешено / запрещено / чужой tenant (DoD, AC-1). */
class UsersAdminIT extends IntegrationTest {

    private UUID tenantA;
    private UUID adminA;
    private UUID tenantB;
    private UUID userB;

    @BeforeEach
    void setUp() {
        tenantA = createTenant("school-a");
        adminA = createUser(tenantA, unique("admin"));
        grantTenantRole(tenantA, adminA, "tenant_admin");
        tenantB = createTenant("school-b");
        userB = createUser(tenantB, unique("user"));
    }

    @Test
    void adminSeesUsersOfOwnTenant() throws Exception {
        mvc.perform(get("/api/v1/users/" + adminA).header(HttpHeaders.AUTHORIZATION, bearer(tenantA, adminA)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tenantRoles[0]").value("tenant_admin"));
        mvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, bearer(tenantA, adminA)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].id", hasItem(adminA.toString())))
            .andExpect(jsonPath("$.items[*].id", not(hasItem(userB.toString()))));
    }

    @Test
    void userOfAnotherTenantIsNotFound() throws Exception {
        String auth = bearer(tenantA, adminA);

        mvc.perform(get("/api/v1/users/" + userB).header(HttpHeaders.AUTHORIZATION, auth))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("user.not_found"));
        mvc.perform(patch("/api/v1/users/" + userB).header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"suspended\"}"))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/users/" + userB + "/invite").header(HttpHeaders.AUTHORIZATION, auth))
            .andExpect(status().isNotFound());
    }

    @Test
    void studentCannotViewOrManageUsers() throws Exception {
        UUID student = createUser(tenantA, unique("student"));
        String auth = bearer(tenantA, student);

        mvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, auth))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("access.denied"));
        mvc.perform(get("/api/v1/users/" + adminA).header(HttpHeaders.AUTHORIZATION, auth))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/users").header(HttpHeaders.AUTHORIZATION, auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + unique("new") + "\",\"firstName\":\"A\",\"lastName\":\"B\",\"sendInvite\":false}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/users/import/commit").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"previewId\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminCannotSuspendSelf() throws Exception {
        mvc.perform(patch("/api/v1/users/" + adminA).header(HttpHeaders.AUTHORIZATION, bearer(tenantA, adminA))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"suspended\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("user.cannot_suspend_self"));
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }
}
