package com.tutorcraft.core.enrollment.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.tutorcraft.core.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Записи, самозапись, приглашения и группы (FR-ENROL-01..05): разрешено / запрещено / чужой tenant. */
class EnrollmentIT extends IntegrationTest {

    private static final String API = "/api/v1";

    @Autowired
    private ObjectMapper json;

    private UUID tenant;
    private UUID teacher;
    private UUID student;
    private String courseId;

    @BeforeEach
    void setUp() throws Exception {
        tenant = createTenant("enrol");
        teacher = createUser(tenant, unique("teacher"));
        grantTenantRole(tenant, teacher, "tenant_admin");
        student = createUser(tenant, unique("student"));
        courseId = createPublishedCourse();
    }

    @Test
    void selfEnrolmentHonoursCodeAndReturnsExistingEnrollment() throws Exception {
        patchCourse("{\"selfEnrol\":{\"enabled\":true,\"code\":\"KEY-1\",\"maxStudents\":10,\"until\":null}}", 1);

        perform(post(API + "/courses/" + courseId + "/self-enrol").contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"wrong\"}"), student)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("enrollment.invalid_code"));
        perform(post(API + "/courses/" + courseId + "/self-enrol").contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"KEY-1\"}"), student)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("student"))
            .andExpect(jsonPath("$.method").value("self"));
        perform(post(API + "/courses/" + courseId + "/self-enrol").contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"KEY-1\"}"), student)
            .andExpect(status().isOk());

        perform(get(API + "/courses/" + courseId), student)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.myRole").value("student"))
            .andExpect(jsonPath("$.selfEnrol.code").value(nullValue()));
    }

    @Test
    void paidCourseRequiresPayment() throws Exception {
        patchCourse("{\"selfEnrol\":{\"enabled\":true},\"price\":{\"amountMinor\":500000,\"currency\":\"RUB\"}}", 1)
            .andExpect(status().isBadRequest());
        perform(put(API + "/courses/" + courseId + "/price").contentType(MediaType.APPLICATION_JSON)
                .content("{\"price\":{\"amountMinor\":500000,\"currency\":\"RUB\"}}"), teacher)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.price.amountMinor").value(500000));

        perform(post(API + "/courses/" + courseId + "/self-enrol"), student)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("enrollment.payment_required"));
    }

    @Test
    void inviteLinkEnrolsOnceAndIsTenantScoped() throws Exception {
        String body = perform(post(API + "/courses/" + courseId + "/invite-links").contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"student\",\"maxUses\":1}"), teacher)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.url", startsWith("http")))
            .andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(body, "$.url");
        String token = url.substring(url.lastIndexOf('/') + 1);

        UUID foreignTenant = createTenant("enrol-foreign");
        UUID outsider = createUser(foreignTenant, unique("outsider"));
        mvc.perform(post(API + "/invite-links/accept").header(HttpHeaders.AUTHORIZATION, bearer(foreignTenant, outsider))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("token", token))))
            .andExpect(status().isNotFound());

        perform(post(API + "/invite-links/accept").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("token", token))), student)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.courseId").value(courseId));
        UUID another = createUser(tenant, unique("late"));
        perform(post(API + "/invite-links/accept").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("token", token))), another)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("enrollment.invite_invalid"));

        perform(get(API + "/courses/" + courseId + "/invite-links"), teacher)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].uses").value(1))
            .andExpect(jsonPath("$[0].active").value(false));
    }

    @Test
    void teacherManagesEnrollmentsAndGroupsStudentCannot() throws Exception {
        enrol(student, "student");

        perform(get(API + "/courses/" + courseId + "/enrollments"), student).andExpect(status().isForbidden());
        String body = perform(get(API + "/courses/" + courseId + "/enrollments").param("role", "student"), teacher)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].user.id").value(student.toString()))
            .andReturn().getResponse().getContentAsString();
        String enrollmentId = JsonPath.read(body, "$.items[0].id");

        String groups = perform(post(API + "/courses/" + courseId + "/groups/auto").contentType(MediaType.APPLICATION_JSON)
                .content("{\"strategy\":\"by_count\",\"value\":2}"), teacher)
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        List<String> memberIds = JsonPath.read(groups, "$[*].memberIds[*]");
        assertThat(memberIds).containsExactly(student.toString());

        perform(patch(API + "/enrollments/" + enrollmentId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"suspended\"}"), teacher)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("suspended"));
        perform(get(API + "/courses/" + courseId + "/outline"), student).andExpect(status().isForbidden());
        perform(delete(API + "/enrollments/" + enrollmentId), teacher).andExpect(status().isNoContent());
    }

    @Test
    void lastTeacherCannotBeRemoved() throws Exception {
        String body = perform(get(API + "/courses/" + courseId + "/enrollments").param("role", "teacher"), teacher)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].user.id", hasItem(teacher.toString())))
            .andReturn().getResponse().getContentAsString();
        String enrollmentId = JsonPath.read(body, "$.items[0].id");

        perform(delete(API + "/enrollments/" + enrollmentId), teacher)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("enrollment.last_teacher"));
    }

    private String createPublishedCourse() throws Exception {
        String body = perform(post(API + "/courses").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Enrollment course\"}"), teacher)
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        perform(patch(API + "/courses/" + id).header(HttpHeaders.IF_MATCH, "\"0\"").contentType(MediaType.APPLICATION_JSON)
                .content("{\"visibility\":\"published\"}"), teacher)
            .andExpect(status().isOk());
        return id;
    }

    private ResultActions patchCourse(String content, long version) throws Exception {
        return perform(patch(API + "/courses/" + courseId).header(HttpHeaders.IF_MATCH, "\"" + version + "\"")
                .contentType(MediaType.APPLICATION_JSON).content(content), teacher);
    }

    private void enrol(UUID userId, String role) throws Exception {
        perform(post(API + "/courses/" + courseId + "/enrollments").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("userIds", List.of(userId.toString()), "role", role))), teacher)
            .andExpect(status().isCreated());
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, UUID userId) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(tenant, userId)));
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }
}
