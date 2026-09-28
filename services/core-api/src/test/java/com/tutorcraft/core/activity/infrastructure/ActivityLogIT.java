package com.tutorcraft.core.activity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.tutorcraft.core.shared.web.RequestCorrelation;
import com.tutorcraft.core.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** Журнал активности: запись запросов с контекстом страницы, видимость только в своей школе, трассировка. */
class ActivityLogIT extends IntegrationTest {

    private static final String SESSION = "tab-it-12345678";

    private static final String TENANT_HEADER = "X-Tenant-Id";

    @Autowired
    private BufferedActivitySink sink;

    private String platformAdmin() {
        UUID platformTenant = createTenant("platform");
        return platformAdminBearer(platformTenant, createPlatformAdmin(platformTenant, unique("root")));
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@activity.test";
    }

    @Test
    void requestIsLoggedWithPageAndTraceable() throws Exception {
        UUID school = createTenant("activity");
        UUID student = createUser(school, unique("student"));
        String missingCourse = "/api/v1/courses/" + UUID.randomUUID();

        String requestId = mvc.perform(get(missingCourse)
                        .header(HttpHeaders.AUTHORIZATION, bearer(school, student))
                        .header(RequestCorrelation.CLIENT_PAGE_HEADER, "/courses/x?token=secret")
                        .header(RequestCorrelation.CLIENT_SESSION_HEADER, SESSION))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.requestId").isNotEmpty())
            .andReturn().getResponse().getHeader(RequestCorrelation.REQUEST_ID_HEADER);
        sink.flush();

        String body = mvc.perform(get("/api/v1/activity-log").param("requestId", requestId)
                        .header(HttpHeaders.AUTHORIZATION, platformAdmin()).header(TENANT_HEADER, school))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].route").value("/api/v1/courses/{id}"))
            .andExpect(jsonPath("$.items[0].status").value(404))
            .andExpect(jsonPath("$.items[0].page").value("/courses/x"))
            .andExpect(jsonPath("$.items[0].sessionId").value(SESSION))
            .andExpect(jsonPath("$.items[0].errorCode").value("course.not_found"))
            .andReturn().getResponse().getContentAsString();
        String entryId = JsonPath.read(body, "$.items[0].id");

        mvc.perform(get("/api/v1/activity-log/" + entryId + "/trail")
                        .header(HttpHeaders.AUTHORIZATION, platformAdmin()).header(TENANT_HEADER, school))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.anchor").value("user"))
            .andExpect(jsonPath("$.events[0].requestId").value(requestId));
    }

    @Test
    void entriesStayInsideTheirSchool() throws Exception {
        UUID school = createTenant("activity-a");
        UUID owner = createUser(school, unique("owner"));
        grantTenantRole(school, owner, "tenant_admin");
        UUID otherSchool = createTenant("activity-b");

        String requestId = mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer(school, owner)))
            .andReturn().getResponse().getHeader(RequestCorrelation.REQUEST_ID_HEADER);
        sink.flush();

        mvc.perform(get("/api/v1/activity-log").param("requestId", requestId)
                        .header(HttpHeaders.AUTHORIZATION, platformAdmin()).header(TENANT_HEADER, otherSchool))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get("/api/v1/activity-log").header(HttpHeaders.AUTHORIZATION, bearer(school, owner)))
            .andExpect(status().isForbidden());
    }

    @Test
    void clientErrorsAreAcceptedAndMasked() throws Exception {
        UUID tenant = createTenant("activity-client");
        UUID user = createUser(tenant, unique("user"));

        mvc.perform(post("/api/v1/activity/events").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tenant, user))
                        .header(RequestCorrelation.CLIENT_SESSION_HEADER, SESSION)
                        .content("""
                            {"events":[{"kind":"client_error","page":"/home","name":"TypeError",
                                        "message":"failed for ivan@mail.ru"}]}
                            """))
            .andExpect(status().isNoContent());
        sink.flush();

        String message = jdbc.sql("SELECT error_message FROM activity_log WHERE user_id = :user AND kind = 'client_error'")
            .param("user", user).query(String.class).single();
        assertThat(message).isEqualTo("failed for ***");

        mvc.perform(post("/api/v1/activity/events").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tenant, user))
                        .content("{\"events\":[{\"kind\":\"request\"}]}"))
            .andExpect(status().isBadRequest());
    }
}
