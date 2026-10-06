package com.tutorcraft.core.org.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mongodb.client.model.Filters;
import com.tutorcraft.core.shared.domain.Ids;
import com.tutorcraft.core.support.IntegrationTest;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** Полное удаление школы главным администратором и журналы всех школ (DELETE /platform/tenants/{id}). */
class PlatformTenantDeletionIT extends IntegrationTest {

    private static final String MONGO_COLLECTION = "modules";

    @Autowired
    private MongoTemplate mongo;

    @Test
    void platformAdminDeletesSchoolWithOwnerAndEverySchoolSeesTheRecord() throws Exception {
        UUID platform = createTenant("platform-deletion");
        UUID admin = createPlatformAdmin(platform, "admin-" + Ids.newId() + "@platform.test");
        String adminToken = platformAdminBearer(platform, admin);

        UUID school = createTenant("doomed-school");
        UUID owner = createUser(school, "owner@doomed.test");
        grantTenantRole(school, owner, "tenant_admin");
        UUID student = createUser(school, "student@doomed.test");
        UUID course = insertCourse(school, owner);
        insertGradeWithHistory(school, course, student);
        mongo.getCollection(MONGO_COLLECTION).insertOne(new Document("_id", Ids.newId()).append("tenantId", school)
                .append("courseId", course));

        UUID neighbour = createTenant("neighbour-school");
        UUID neighbourUser = createUser(neighbour, "kept@neighbour.test");
        jdbc.sql("UPDATE users SET created_by = :owner WHERE id = :id").param("owner", owner).param("id", neighbourUser).update();
        String slug = jdbc.sql("SELECT slug FROM tenants WHERE id = :id").param("id", school).query(String.class).single();

        mvc.perform(delete("/api/v1/platform/tenants/{id}", school).header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirmSlug\":\"wrong\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/platform/tenants/{id}", school)
                        .header(HttpHeaders.AUTHORIZATION, bearer(school, owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirmSlug\":\"" + slug + "\"}"))
            .andExpect(status().isForbidden());

        mvc.perform(delete("/api/v1/platform/tenants/{id}", school).header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirmSlug\":\"" + slug + "\"}"))
            .andExpect(status().isNoContent());

        assertThat(count("SELECT count(*) FROM tenants WHERE id = :id", school)).isZero();
        assertThat(count("SELECT count(*) FROM users WHERE tenant_id = :id", school)).isZero();
        assertThat(count("SELECT count(*) FROM courses WHERE tenant_id = :id", school)).isZero();
        assertThat(count("SELECT count(*) FROM grade_history WHERE tenant_id = :id", school)).isZero();
        assertThat(count("SELECT count(*) FROM users WHERE id = :id AND created_by IS NULL", neighbourUser)).isOne();
        assertThat(mongo.getCollection(MONGO_COLLECTION).countDocuments(Filters.eq("tenantId", school))).isZero();

        mvc.perform(get("/api/v1/audit-log").param("allTenants", "true").param("objectType", "tenant")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[?(@.objectId == '%s')].action", school.toString()).value("tenant.deleted"));
        mvc.perform(get("/api/v1/activity-log").param("allTenants", "true").header(HttpHeaders.AUTHORIZATION, adminToken))
            .andExpect(status().isOk());
    }

    @Test
    void everySchoolLogsAndCourseCreationAreClosedToOthers() throws Exception {
        UUID platform = createTenant("platform-logs");
        UUID admin = createPlatformAdmin(platform, "admin-" + Ids.newId() + "@platform.test");
        UUID school = createTenant("tutor-school");
        UUID owner = createUser(school, "owner@tutor.test");
        grantTenantRole(school, owner, "tenant_admin");

        mvc.perform(get("/api/v1/activity-log").param("allTenants", "true").header(HttpHeaders.AUTHORIZATION, bearer(school, owner)))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/courses").header(HttpHeaders.AUTHORIZATION, platformAdminBearer(platform, admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Admin course\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/platform/tenants/{id}", platform)
                        .header(HttpHeaders.AUTHORIZATION, platformAdminBearer(platform, admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirmSlug\":\"x\"}"))
            .andExpect(status().isUnprocessableEntity());
    }

    private UUID insertCourse(UUID tenantId, UUID createdBy) {
        UUID id = Ids.newId();
        jdbc.sql("""
                INSERT INTO courses (id, tenant_id, title, slug, created_by, created_at, updated_at)
                VALUES (:id, :tenantId, 'Doomed', :slug, :createdBy, now(), now())
                """)
            .param("id", id).param("tenantId", tenantId).param("slug", "doomed-" + id).param("createdBy", createdBy).update();
        return id;
    }

    /** grade_history append-only: удаление школы обязано пройти через флаг очистки. */
    private void insertGradeWithHistory(UUID tenantId, UUID courseId, UUID userId) {
        UUID item = Ids.newId();
        UUID grade = Ids.newId();
        jdbc.sql("""
                INSERT INTO grade_items (id, tenant_id, course_id, name, max_score, created_at, updated_at)
                VALUES (:id, :tenantId, :courseId, 'Item', 10, now(), now())
                """)
            .param("id", item).param("tenantId", tenantId).param("courseId", courseId).update();
        jdbc.sql("""
                INSERT INTO grades (id, tenant_id, grade_item_id, user_id, created_at, updated_at)
                VALUES (:id, :tenantId, :itemId, :userId, now(), now())
                """)
            .param("id", grade).param("tenantId", tenantId).param("itemId", item).param("userId", userId).update();
        jdbc.sql("INSERT INTO grade_history (id, tenant_id, grade_id, reason, at) VALUES (:id, :tenantId, :gradeId, 'set', now())")
            .param("id", Ids.newId()).param("tenantId", tenantId).param("gradeId", grade).update();
    }

    private long count(String sql, UUID id) {
        return jdbc.sql(sql).param("id", id).query(Long.class).single();
    }
}
