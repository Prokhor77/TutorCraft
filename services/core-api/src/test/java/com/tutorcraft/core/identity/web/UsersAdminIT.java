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

/**
 * Администрирование пользователей: только главный администратор (platform_admin), в выбранной школе через
 * X-Tenant-Id; владелец школы и студент — 403; чужой tenant — 404 (DoD, AC-1).
 */
class UsersAdminIT extends IntegrationTest {

    private static final String TENANT_HEADER = "X-Tenant-Id";

    private UUID platformTenant;
    private UUID platformAdmin;
    private UUID tenantA;
    private UUID ownerA;
    private UUID tenantB;
    private UUID userB;

    @BeforeEach
    void setUp() {
        platformTenant = createTenant("platform");
        platformAdmin = createPlatformAdmin(platformTenant, unique("root"));
        tenantA = createTenant("school-a");
        ownerA = createUser(tenantA, unique("owner"));
        grantTenantRole(tenantA, ownerA, "tenant_admin");
        tenantB = createTenant("school-b");
        userB = createUser(tenantB, unique("user"));
    }

    @Test
    void platformAdminSeesUsersOfSelectedSchoolOnly() throws Exception {
        String auth = platformAdminBearer(platformTenant, platformAdmin);

        mvc.perform(get("/api/v1/users/" + ownerA).header(HttpHeaders.AUTHORIZATION, auth).header(TENANT_HEADER, tenantA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tenantRoles[0]").value("tenant_admin"));
        mvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, auth).header(TENANT_HEADER, tenantA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].id", hasItem(ownerA.toString())))
            .andExpect(jsonPath("$.items[*].id", not(hasItem(userB.toString()))));
    }

    @Test
    void userOfAnotherSchoolIsNotFound() throws Exception {
        String auth = platformAdminBearer(platformTenant, platformAdmin);

        mvc.perform(get("/api/v1/users/" + userB).header(HttpHeaders.AUTHORIZATION, auth).header(TENANT_HEADER, tenantA))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("user.not_found"));
        mvc.perform(patch("/api/v1/users/" + userB).header(HttpHeaders.AUTHORIZATION, auth).header(TENANT_HEADER, tenantA)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"suspended\"}"))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/users/" + userB + "/invite").header(HttpHeaders.AUTHORIZATION, auth)
                .header(TENANT_HEADER, tenantA))
            .andExpect(status().isNotFound());
    }

    @Test
    void schoolOwnerHasNoAdministration() throws Exception {
        String auth = bearer(tenantA, ownerA);

        mvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, auth))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("access.denied"));
        mvc.perform(patch("/api/v1/tenant").header(HttpHeaders.AUTHORIZATION, auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Hijacked\",\"defaultLocale\":\"ru\",\"defaultTimezone\":\"Europe/Moscow\",\"version\":0}"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/audit-log").header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/platform/tenants").header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isForbidden());
    }

    @Test
    void tenantHeaderIsIgnoredForNonAdmins() throws Exception {
        mvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, bearer(tenantA, ownerA)).header(TENANT_HEADER, tenantB))
            .andExpect(status().isForbidden());
    }

    @Test
    void forgedPlatformRoleClaimWithoutGrantIsDenied() throws Exception {
        mvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, platformAdminBearer(tenantA, ownerA))
                .header(TENANT_HEADER, tenantB))
            .andExpect(status().isForbidden());
    }

    @Test
    void studentCannotViewOrManageUsers() throws Exception {
        UUID student = createUser(tenantA, unique("student"));
        String auth = bearer(tenantA, student);

        mvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, auth))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("access.denied"));
        mvc.perform(get("/api/v1/users/" + ownerA).header(HttpHeaders.AUTHORIZATION, auth))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/users").header(HttpHeaders.AUTHORIZATION, auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + unique("new") + "\",\"firstName\":\"A\",\"lastName\":\"B\",\"sendInvite\":false}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/users/import/commit").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"previewId\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void platformRoleCannotBeGrantedThroughApi() throws Exception {
        mvc.perform(patch("/api/v1/users/" + ownerA).header(HttpHeaders.AUTHORIZATION, platformAdminBearer(platformTenant, platformAdmin))
                .header(TENANT_HEADER, tenantA).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tenantRoles\":[\"platform_admin\",\"tenant_admin\"]}"))
            .andExpect(status().isBadRequest());
        Integer platformGrants = jdbc.sql("SELECT count(*) FROM role_assignments WHERE user_id = :userId AND context_type = 'platform'")
            .param("userId", ownerA).query(Integer.class).single();
        org.assertj.core.api.Assertions.assertThat(platformGrants).isZero();
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminCannotSuspendSelf() throws Exception {
        mvc.perform(patch("/api/v1/users/" + platformAdmin).header(HttpHeaders.AUTHORIZATION, platformAdminBearer(platformTenant, platformAdmin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"suspended\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("user.cannot_suspend_self"));
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }
}
