package com.tutorcraft.core.assessment.quiz;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.tutorcraft.core.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Общая подготовка сквозных тестов тестов (quiz): курс, модуль, тест, запись студентов и вопросы банка —
 * через публичный REST API модулей courses/enrollment.
 */
public abstract class QuizIntegrationSupport extends IntegrationTest {

    protected static final String API = "/api/v1";

    @Autowired
    protected ObjectMapper json;

    protected UUID tenant;
    protected UUID teacher;

    protected void setUpTenant(String slug) {
        tenant = createTenant(slug);
        teacher = createUser(tenant, unique("teacher"));
        grantTenantRole(tenant, teacher, "tenant_admin");
    }

    protected ResultActions perform(MockHttpServletRequestBuilder request, UUID userId) throws Exception {
        return perform(request, tenant, userId);
    }

    protected ResultActions perform(MockHttpServletRequestBuilder request, UUID tenantId, UUID userId) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(tenantId, userId)));
    }

    protected String send(MockHttpServletRequestBuilder request, Object body, UUID userId, int expectedStatus) throws Exception {
        MockHttpServletRequestBuilder withBody = body == null ? request
                : request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return perform(withBody, userId).andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
    }

    /** Опубликованный курс с одним модулем и тестом; возвращает id теста. */
    protected UUID publishedQuiz(Map<String, Object> settings, List<UUID> students) throws Exception {
        String courseId = JsonPath.read(send(post(API + "/courses"), Map.of("title", "Quiz course"), teacher, 201), "$.id");
        perform(patch(API + "/courses/" + courseId).header(HttpHeaders.IF_MATCH, "\"0\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":\"published\"}"), teacher)
            .andExpect(status().isOk());
        String moduleId = JsonPath.read(send(post(API + "/courses/" + courseId + "/modules"), Map.of("title", "Week 1"),
                teacher, 201), "$.id");
        String itemId = JsonPath.read(send(post(API + "/modules/" + moduleId + "/items"),
                Map.of("type", "quiz", "title", "Final quiz", "settings", settings), teacher, 201), "$.id");
        if (!students.isEmpty()) {
            send(post(API + "/courses/" + courseId + "/enrollments"),
                    Map.of("userIds", students.stream().map(UUID::toString).toList(), "role", "student"), teacher, 201);
        }
        return UUID.fromString(itemId);
    }

    protected UUID courseOf(UUID itemId) throws Exception {
        String body = send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(API + "/items/" + itemId),
                null, teacher, 200);
        return UUID.fromString(JsonPath.read(body, "$.courseId"));
    }

    protected UUID createQuestion(UUID courseId, Map<String, Object> input) throws Exception {
        return UUID.fromString(JsonPath.read(send(post(API + "/courses/" + courseId + "/questions"), input, teacher, 201), "$.id"));
    }

    protected void putSlots(UUID itemId, List<UUID> questionIds) throws Exception {
        List<Map<String, Object>> slots = questionIds.stream()
                .map(id -> Map.<String, Object>of("questionId", id.toString(), "page", 1)).toList();
        send(put(API + "/items/" + itemId + "/quiz/slots"), Map.of("slots", slots), teacher, 200);
    }

    protected static Map<String, Object> question(String type, String title, Map<String, Object> data) {
        return Map.of("type", type, "title", title, "defaultScore", 1, "tags", List.of(), "data", data,
                "body", Map.of("schemaVersion", 1, "blocks", List.of(Map.of("id", "b1", "type", "paragraph",
                        "text", List.of(Map.of("text", title))))));
    }

    protected static Map<String, Object> option(String id, String text, boolean correct) {
        return Map.of("id", id, "text", text, "correct", correct);
    }

    protected static Map<String, Object> reviewImmediately() {
        return Map.of("whenScore", "immediately", "whenCorrectness", "immediately", "whenCorrectAnswers", "immediately",
                "whenFeedback", "immediately");
    }

    protected static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }
}
