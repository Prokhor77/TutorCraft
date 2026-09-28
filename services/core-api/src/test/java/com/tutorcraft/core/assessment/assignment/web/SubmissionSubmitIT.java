package com.tutorcraft.core.assessment.assignment.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.CourseRole;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.assessment.assignment.domain.AssignmentSettings;
import com.tutorcraft.core.courses.CoursesApi;
import com.tutorcraft.core.courses.ItemRef;
import com.tutorcraft.core.courses.ItemType;
import com.tutorcraft.core.courses.Visibility;
import com.tutorcraft.core.enrollment.EnrollmentApi;
import com.tutorcraft.core.shared.domain.ForbiddenException;
import com.tutorcraft.core.shared.domain.Ids;
import com.tutorcraft.core.shared.domain.NotFoundException;
import com.tutorcraft.core.support.IntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * AC-3 (NFR-REL-05): повтор отправки с тем же Idempotency-Key создаёт ровно одну сдачу, время сдачи — первая
 * успешная обработка. Модули courses/enrollment/access замещены моками — проверяется поведение модуля заданий.
 */
class SubmissionSubmitIT extends IntegrationTest {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    @MockBean
    private CoursesApi courses;
    @MockBean
    private EnrollmentApi enrollment;
    @MockBean
    private AccessService access;

    private UUID tenant;
    private UUID student;
    private UUID teacher;
    private UUID courseId;
    private UUID itemId;
    private UUID fileId;

    @BeforeEach
    void setUp() {
        tenant = createTenant("assign");
        student = createUser(tenant, unique("student"));
        teacher = createUser(tenant, unique("teacher"));
        courseId = Ids.newId();
        itemId = Ids.newId();
        fileId = insertReadyFile(tenant, student);
        stubAssignment(Instant.now().plus(7, ChronoUnit.DAYS), null);
        when(courses.isVisibleToLearners(eq(tenant), any())).thenReturn(true);
        when(enrollment.groupIds(any(), any(), any())).thenReturn(Set.of());
        when(enrollment.activeMembers(eq(tenant), eq(courseId), any()))
                .thenReturn(List.of(new EnrollmentApi.Member(teacher, CourseRole.TEACHER, "active", Set.of())));
    }

    @Test
    void repeatedSubmitWithSameKeyCreatesExactlyOneSubmission() throws Exception {
        saveDraftWithFile().andExpect(status().isOk()).andExpect(jsonPath("$.status").value("draft"));
        String key = UUID.randomUUID().toString();

        MvcResult first = submit(key).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("submitted"))
                .andExpect(jsonPath("$.late").value(false))
                .andReturn();
        String submissionId = JsonPath.read(first.getResponse().getContentAsString(), "$.id");
        String submittedAt = JsonPath.read(first.getResponse().getContentAsString(), "$.submittedAt");

        submit(key).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(submissionId))
                .andExpect(jsonPath("$.submittedAt").value(submittedAt))
                .andExpect(jsonPath("$.status").value("submitted"));

        assertThat(count("SELECT COUNT(*) FROM submissions WHERE tenant_id = :tenantId AND item_id = :itemId")).isEqualTo(1);
        assertThat(count("""
                SELECT COUNT(*) FROM notifications WHERE tenant_id = :tenantId AND category = 'submission_received'
                """)).isEqualTo(1);
        mvc.perform(get("/api/v1/items/" + itemId + "/my-submission").header(HttpHeaders.AUTHORIZATION, bearer(tenant, student)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("submitted"))
            .andExpect(jsonPath("$.files[0].id").value(fileId.toString()));
    }

    @Test
    void submitWithNewKeyAfterSubmissionIsRejected() throws Exception {
        saveDraftWithFile().andExpect(status().isOk());
        submit(UUID.randomUUID().toString()).andExpect(status().isOk());

        submit(UUID.randomUUID().toString())
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("assignment.already_submitted"));
    }

    @Test
    void submitWithoutIdempotencyKeyIsBadRequest() throws Exception {
        saveDraftWithFile().andExpect(status().isOk());

        mvc.perform(post("/api/v1/items/" + itemId + "/my-submission/submit").header(HttpHeaders.AUTHORIZATION, bearer(tenant, student)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void hardCloseBlocksSubmission() throws Exception {
        stubAssignment(Instant.now().minus(2, ChronoUnit.DAYS), Instant.now().minus(1, ChronoUnit.DAYS));

        saveDraftWithFile()
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("assignment.closed"));
    }

    @Test
    void userWithoutPermissionIsForbidden() throws Exception {
        doThrow(new ForbiddenException("access.denied", "denied"))
                .when(access).require(eq(Permission.SUBMISSION_SUBMIT), any());

        mvc.perform(get("/api/v1/items/" + itemId + "/my-submission").header(HttpHeaders.AUTHORIZATION, bearer(tenant, student)))
            .andExpect(status().isForbidden());
    }

    @Test
    void itemOfAnotherTenantIsNotFound() throws Exception {
        UUID otherTenant = createTenant("other");
        UUID stranger = createUser(otherTenant, unique("stranger"));
        when(courses.requireItem(otherTenant, itemId)).thenThrow(new NotFoundException("item.not_found", "Item not found"));

        mvc.perform(get("/api/v1/items/" + itemId + "/my-submission").header(HttpHeaders.AUTHORIZATION, bearer(otherTenant, stranger)))
            .andExpect(status().isNotFound());
    }

    private ResultActions saveDraftWithFile() throws Exception {
        return mvc.perform(put("/api/v1/items/" + itemId + "/my-submission/draft")
                .header(HttpHeaders.AUTHORIZATION, bearer(tenant, student))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fileIds\":[\"" + fileId + "\"]}"));
    }

    private ResultActions submit(String key) throws Exception {
        return mvc.perform(post("/api/v1/items/" + itemId + "/my-submission/submit")
                .header(HttpHeaders.AUTHORIZATION, bearer(tenant, student))
                .header(IDEMPOTENCY_KEY, key));
    }

    private void stubAssignment(Instant dueAt, Instant closeAt) {
        Map<String, Object> settings = new HashMap<>(AssignmentSettings.defaults().toMap());
        settings.put("dueAt", dueAt.toString());
        settings.put("closeAt", closeAt == null ? null : closeAt.toString());
        ItemRef item = new ItemRef(itemId, tenant, courseId, Ids.newId(), ItemType.ASSIGNMENT, "Эссе", 0, Visibility.PUBLISHED,
                null, dueAt, null, closeAt, settings, Map.of("mode", "none"), null, 1);
        when(courses.requireItem(tenant, itemId)).thenReturn(item);
        when(courses.findItems(eq(tenant), any())).thenReturn(Map.of(itemId, item));
    }

    private UUID insertReadyFile(UUID tenantId, UUID ownerId) {
        UUID id = Ids.newId();
        jdbc.sql("""
                INSERT INTO files (id, tenant_id, uploaded_by, name, size_bytes, declared_mime, mime, purpose, status,
                                   storage_key, created_at, completed_at)
                VALUES (:id, :tenantId, :ownerId, 'essay.pdf', 1024, 'application/pdf', 'application/pdf', 'submission', 'ready',
                        :key, :now, :now)
                """)
            .param("id", id).param("tenantId", tenantId).param("ownerId", ownerId).param("key", "t/" + tenantId + "/" + id)
            .param("now", Timestamp.from(Instant.now()))
            .update();
        return id;
    }

    private long count(String sql) {
        return jdbc.sql(sql).param("tenantId", tenant).param("itemId", itemId).query(Long.class).single();
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.test";
    }
}
