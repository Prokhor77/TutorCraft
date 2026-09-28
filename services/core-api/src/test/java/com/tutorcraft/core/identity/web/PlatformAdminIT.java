package com.tutorcraft.core.identity.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.tutorcraft.core.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** Главный администратор из конфигурации (application-test.yml → tutorcraft.admin) создаётся при старте. */
class PlatformAdminIT extends IntegrationTest {

    private static final String EMAIL = "admin@tutorcraft.test";
    private static final String PASSWORD = "TestAdmin-Passw0rd";

    @Test
    void configuredAdminLogsInAndListsSchools() throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.tenantRoles[0]").value("platform_admin"))
            .andReturn().getResponse().getContentAsString();
        String accessToken = JsonPath.read(body, "$.accessToken");

        createTenant("listed-school");
        mvc.perform(get("/api/v1/platform/tenants").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].slug").isNotEmpty());
    }

    @Test
    void platformRoleBelongsToConfiguredAdminOnly() {
        Integer holders = jdbc.sql("""
                SELECT count(DISTINCT ra.user_id) FROM role_assignments ra
                JOIN users u ON u.id = ra.user_id
                WHERE ra.context_type = 'platform' AND u.email = :email
                """)
            .param("email", EMAIL).query(Integer.class).single();
        org.assertj.core.api.Assertions.assertThat(holders).isEqualTo(1);
    }
}
