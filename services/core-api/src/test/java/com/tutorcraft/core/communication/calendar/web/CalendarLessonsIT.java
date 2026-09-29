package com.tutorcraft.core.communication.calendar.web;

import static org.hamcrest.Matchers.hasSize;
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
import java.util.HashMap;
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

/** Занятия и заметки в календаре: разрешено / запрещено / чужой tenant, адресация ученикам. */
class CalendarLessonsIT extends IntegrationTest {

    private static final String API = "/api/v1";
    private static final String FROM = "2026-10-01T00:00:00Z";
    private static final String TO = "2026-11-01T00:00:00Z";
    private static final String STARTS_AT = "2026-10-05T15:00:00Z";

    @Autowired
    private ObjectMapper json;

    private UUID tenant;
    private UUID teacher;
    private UUID anna;
    private UUID boris;
    private String courseId;

    @BeforeEach
    void setUp() throws Exception {
        tenant = createTenant("calendar");
        teacher = createUser(tenant, unique("teacher"));
        grantTenantRole(tenant, teacher, "tenant_admin");
        anna = createUser(tenant, unique("anna"));
        boris = createUser(tenant, unique("boris"));
        courseId = createCourse();
        enrol(List.of(anna, boris));
    }

    @Test
    void personalLessonIsVisibleOnlyToChosenStudent() throws Exception {
        String lessonId = createLesson(Map.of("title", "Разбор ДЗ", "startsAt", STARTS_AT,
                "attendeeIds", List.of(anna.toString())));

        calendar(anna).andExpect(jsonPath("$[?(@.kind == 'lesson')]", hasSize(1)))
            .andExpect(jsonPath("$[?(@.kind == 'lesson')].attendeeIds[*]", hasSize(0)))
            .andExpect(jsonPath("$[?(@.kind == 'lesson')].canEdit").value(false));
        calendar(boris).andExpect(jsonPath("$[?(@.kind == 'lesson')]", hasSize(0)));
        calendar(teacher).andExpect(jsonPath("$[?(@.id == '" + lessonId + "')].canEdit").value(true))
            .andExpect(jsonPath("$[?(@.id == '" + lessonId + "')].attendeeIds[0]").value(anna.toString()));
    }

    @Test
    void courseLessonReachesAllStudentsAndCanBeRescheduledWithVersion() throws Exception {
        String lessonId = createLesson(Map.of("title", "Урок 1", "startsAt", STARTS_AT));

        calendar(boris).andExpect(jsonPath("$[?(@.kind == 'lesson')].audience").value("course"));
        Map<String, Object> body = new HashMap<>(Map.of("title", "Урок 1", "startsAt", "2026-10-06T15:00:00Z"));
        perform(put(lessonUrl(lessonId)).header(HttpHeaders.IF_MATCH, "\"5\"").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)), teacher)
            .andExpect(status().isConflict());
        perform(put(lessonUrl(lessonId)).header(HttpHeaders.IF_MATCH, "\"0\"").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)), teacher)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(1));
        perform(delete(lessonUrl(lessonId)), teacher).andExpect(status().isNoContent());
        calendar(anna).andExpect(jsonPath("$[?(@.kind == 'lesson')]", hasSize(0)));
    }

    @Test
    void studentCannotScheduleAndForeignTenantGetsNotFound() throws Exception {
        String body = json.writeValueAsString(Map.of("title", "Урок", "startsAt", STARTS_AT));
        perform(post(lessonsUrl()).contentType(MediaType.APPLICATION_JSON).content(body), anna)
            .andExpect(status().isForbidden());

        UUID foreignTenant = createTenant("calendar-foreign");
        UUID outsider = createUser(foreignTenant, unique("outsider"));
        mvc.perform(post(lessonsUrl()).header(HttpHeaders.AUTHORIZATION, bearer(foreignTenant, outsider))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isNotFound());
    }

    @Test
    void attendeesMustBeStudentsOfTheCourse() throws Exception {
        UUID stranger = createUser(tenant, unique("stranger"));
        perform(post(lessonsUrl()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                "title", "Урок", "startsAt", STARTS_AT, "attendeeIds", List.of(stranger.toString())))), teacher)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("attendeeIds"));
        perform(get(API + "/courses/" + courseId + "/calendar/students"), teacher)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)));
        perform(get(API + "/me/calendar/lesson-courses"), teacher)
            .andExpect(jsonPath("$[0].id").value(courseId));
        perform(get(API + "/me/calendar/lesson-courses"), anna).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void noteKeepsDescriptionAndAllDayFlag() throws Exception {
        perform(post(API + "/me/calendar/events").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                Map.of("title", "Контрольная", "description", "Взять циркуль", "startsAt", STARTS_AT, "allDay", true))), anna)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.kind").value("personal"))
            .andExpect(jsonPath("$.description").value("Взять циркуль"))
            .andExpect(jsonPath("$.allDay").value(true))
            .andExpect(jsonPath("$.canEdit").value(true));
    }

    private String createLesson(Map<String, Object> body) throws Exception {
        String response = perform(post(lessonsUrl()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)), teacher)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.kind").value("lesson"))
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private ResultActions calendar(UUID userId) throws Exception {
        return perform(get(API + "/me/calendar").param("from", FROM).param("to", TO), userId).andExpect(status().isOk());
    }

    private String createCourse() throws Exception {
        String body = perform(post(API + "/courses").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Calendar course\"}"), teacher)
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        perform(patch(API + "/courses/" + id).header(HttpHeaders.IF_MATCH, "\"0\"").contentType(MediaType.APPLICATION_JSON)
                .content("{\"visibility\":\"published\"}"), teacher)
            .andExpect(status().isOk());
        return id;
    }

    private void enrol(List<UUID> userIds) throws Exception {
        List<String> ids = userIds.stream().map(UUID::toString).toList();
        perform(post(API + "/courses/" + courseId + "/enrollments").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("userIds", ids, "role", "student"))), teacher)
            .andExpect(status().isCreated());
    }

    private String lessonsUrl() {
        return API + "/courses/" + courseId + "/calendar/lessons";
    }

    private String lessonUrl(String lessonId) {
        return lessonsUrl() + "/" + lessonId;
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, UUID userId) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(tenant, userId)));
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }
}
