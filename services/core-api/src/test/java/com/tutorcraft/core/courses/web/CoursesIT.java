package com.tutorcraft.core.courses.web;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.tutorcraft.core.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Курсы и структура: AC-1 (изоляция tenant), AC-2 (задание по названию), видимость скрытого для студента/преподавателя. */
class CoursesIT extends IntegrationTest {

    private static final String API = "/api/v1";

    @Autowired
    private ObjectMapper json;

    private UUID tenantA;
    private UUID teacherA;
    private UUID studentA;

    @BeforeEach
    void setUp() {
        tenantA = createTenant("courses-a");
        teacherA = createUser(tenantA, unique("teacher"));
        grantTenantRole(tenantA, teacherA, "tenant_admin");
        studentA = createUser(tenantA, unique("student"));
    }

    /** AC-1: администратор tenant A запрашивает курс tenant B по прямому ID → 404 и запись в аудите. */
    @Test
    void courseOfAnotherTenantIsNotFoundAndAudited() throws Exception {
        UUID tenantB = createTenant("courses-b");
        UUID adminB = createUser(tenantB, unique("admin-b"));
        grantTenantRole(tenantB, adminB, "tenant_admin");
        String courseB = createCourse(tenantB, adminB, "Course of B");

        perform(get(API + "/courses/" + courseB), tenantA, teacherA)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("course.not_found"));
        perform(get(API + "/courses/" + courseB + "/outline"), tenantA, teacherA).andExpect(status().isNotFound());
        perform(post(API + "/courses/" + courseB + "/modules").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Injected\"}"), tenantA, teacherA)
            .andExpect(status().isNotFound());

        Integer audited = jdbc.sql("""
                SELECT count(*) FROM audit_log
                WHERE tenant_id = :tenantId AND actor_id = :actorId AND action = 'access.cross_tenant_denied'
                  AND object_type = 'course' AND object_id = :courseId
                """)
            .param("tenantId", tenantA).param("actorId", teacherA).param("courseId", courseB)
            .query(Integer.class).single();
        Assertions.assertThat(audited).isGreaterThanOrEqualTo(1);
    }

    /** AC-2: задание создаётся по одному названию (3 запроса) со значениями по умолчанию и видимостью модуля. */
    @Test
    void assignmentIsCreatedFromTitleOnlyWithDefaults() throws Exception {
        String courseId = createCourse(tenantA, teacherA, "Algebra");
        String moduleId = createModule(courseId, "Week 1");

        perform(post(API + "/modules/" + moduleId + "/items").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"assignment\",\"title\":\"Homework 1\"}"), tenantA, teacherA)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.type").value("assignment"))
            .andExpect(jsonPath("$.title").value("Homework 1"))
            .andExpect(jsonPath("$.moduleId").value(moduleId))
            .andExpect(jsonPath("$.visibility").value("published"))
            .andExpect(jsonPath("$.settings.submissionType").value("file"))
            .andExpect(jsonPath("$.settings.maxScore").value(100))
            .andExpect(jsonPath("$.settings.dueAt").value(nullValue()))
            .andExpect(jsonPath("$.dueAt").value(nullValue()));

        perform(get(API + "/courses/" + courseId + "/outline"), tenantA, teacherA)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.modules[0].items[0].title").value("Homework 1"));
    }

    @Test
    void newItemInheritsHiddenModuleVisibility() throws Exception {
        String courseId = createCourse(tenantA, teacherA, "Geometry");
        String moduleId = createModule(courseId, "Draft week");
        perform(patch(API + "/modules/" + moduleId).header(HttpHeaders.IF_MATCH, "\"0\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":\"hidden\"}"), tenantA, teacherA)
            .andExpect(status().isOk());

        perform(post(API + "/modules/" + moduleId + "/items").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"page\",\"title\":\"Notes\"}"), tenantA, teacherA)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.visibility").value("hidden"));
    }

    @Test
    void studentDoesNotSeeHiddenItemButTeacherDoes() throws Exception {
        String courseId = createCourse(tenantA, teacherA, "Physics");
        publishCourse(courseId);
        String moduleId = createModule(courseId, "Mechanics");
        String visibleId = createItem(moduleId, "page", "Newton laws");
        String hiddenId = createItem(moduleId, "page", "Exam answers");
        perform(patch(API + "/items/" + hiddenId).header(HttpHeaders.IF_MATCH, "\"0\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":\"hidden\"}"), tenantA, teacherA)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(1));
        enrol(courseId, studentA, "student");

        perform(get(API + "/courses/" + courseId + "/outline"), tenantA, studentA)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.modules[0].items[*].id", hasItem(visibleId)))
            .andExpect(jsonPath("$.modules[0].items[*].id", not(hasItem(hiddenId))));
        perform(get(API + "/items/" + hiddenId), tenantA, studentA)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("item.not_found"));
        perform(get(API + "/items/" + visibleId), tenantA, studentA)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.permissions", hasItem("content.view")));

        perform(get(API + "/courses/" + courseId + "/outline"), tenantA, teacherA)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.modules[0].items[*].id", hasItem(hiddenId)));
        perform(get(API + "/items/" + hiddenId), tenantA, teacherA)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.visibility").value("hidden"))
            .andExpect(jsonPath("$.permissions", hasItem("course.viewHidden")));
    }

    @Test
    void hiddenCourseIsNotAvailableToEnrolledStudent() throws Exception {
        String courseId = createCourse(tenantA, teacherA, "Draft course");
        enrol(courseId, studentA, "student");

        perform(get(API + "/courses/" + courseId + "/outline"), tenantA, studentA)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("course.hidden"));
    }

    @Test
    void studentCannotEditStructure() throws Exception {
        String courseId = createCourse(tenantA, teacherA, "Chemistry");
        publishCourse(courseId);
        String moduleId = createModule(courseId, "Atoms");
        enrol(courseId, studentA, "student");

        perform(post(API + "/modules/" + moduleId + "/items").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"page\",\"title\":\"Hack\"}"), tenantA, studentA)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("access.denied"));
    }

    @Test
    void staleVersionIsRejected() throws Exception {
        String courseId = createCourse(tenantA, teacherA, "Biology");

        perform(patch(API + "/courses/" + courseId).header(HttpHeaders.IF_MATCH, "\"7\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Renamed\"}"), tenantA, teacherA)
            .andExpect(status().isPreconditionFailed())
            .andExpect(jsonPath("$.code").value("conflict.version"));
    }

    @Test
    void deletedItemGoesToTrashAndCanBeRestored() throws Exception {
        String courseId = createCourse(tenantA, teacherA, "History");
        String moduleId = createModule(courseId, "Antiquity");
        String itemId = createItem(moduleId, "page", "Rome");

        perform(delete(API + "/items/" + itemId), tenantA, teacherA).andExpect(status().isNoContent());
        perform(get(API + "/trash").param("courseId", courseId), tenantA, teacherA)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(itemId))
            .andExpect(jsonPath("$[0].kind").value("item"));
        perform(post(API + "/items/" + itemId + "/restore"), tenantA, teacherA).andExpect(status().isNoContent());

        perform(get(API + "/items/" + itemId), tenantA, teacherA).andExpect(status().isOk());
    }

    @Test
    void publishedCourseAppearsInPublicCatalog() throws Exception {
        String courseId = createCourse(tenantA, teacherA, "Public course");
        publishCourse(courseId);
        String tenantSlug = jdbc.sql("SELECT slug FROM tenants WHERE id = :id").param("id", tenantA).query(String.class).single();

        mvc.perform(get(API + "/public/" + tenantSlug + "/courses"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].id", hasItem(courseId)));
        mvc.perform(get(API + "/public/" + tenantSlug + "/courses/public-course"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Public course"))
            .andExpect(jsonPath("$.teacher.name").value("Test User"));
    }

    private ResultActions perform(MockHttpServletRequestBuilder request,
                                  UUID tenantId, UUID userId) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(tenantId, userId)));
    }

    private String createCourse(UUID tenantId, UUID userId, String title) throws Exception {
        String body = perform(post(API + "/courses").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("title", title))), tenantId, userId)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.myRole").value("teacher"))
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void publishCourse(String courseId) throws Exception {
        perform(patch(API + "/courses/" + courseId).header(HttpHeaders.IF_MATCH, "\"0\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":\"published\"}"), tenantA, teacherA)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.visibility").value("published"));
    }

    private String createModule(String courseId, String title) throws Exception {
        String body = perform(post(API + "/courses/" + courseId + "/modules").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("title", title))), tenantA, teacherA)
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String createItem(String moduleId, String type, String title) throws Exception {
        String body = perform(post(API + "/modules/" + moduleId + "/items").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("type", type, "title", title))), tenantA, teacherA)
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void enrol(String courseId, UUID userId, String role) throws Exception {
        perform(post(API + "/courses/" + courseId + "/enrollments").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("userIds", List.of(userId.toString()), "role", role))), tenantA, teacherA)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.created").value(1));
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }
}
