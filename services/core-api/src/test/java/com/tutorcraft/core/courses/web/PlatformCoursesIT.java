package com.tutorcraft.core.courses.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tutorcraft.core.access.domain.CourseRole;
import com.tutorcraft.core.enrollment.EnrollmentApi;
import com.tutorcraft.core.enrollment.EnrollmentApi.EnrolCommand;
import com.tutorcraft.core.shared.domain.Ids;
import com.tutorcraft.core.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

/** Курсы всех школ для главного администратора (GET /platform/courses). */
class PlatformCoursesIT extends IntegrationTest {

    @Autowired
    private EnrollmentApi enrollment;

    @Test
    void platformAdminSeesCoursesOfEverySchoolWithAuthorAndHeadcount() throws Exception {
        UUID platform = createTenant("platform-courses");
        UUID admin = createPlatformAdmin(platform, "admin-" + Ids.newId() + "@platform.test");
        String adminToken = platformAdminBearer(platform, admin);

        UUID school = createTenant("courses-school");
        UUID owner = createUser(school, "owner-" + Ids.newId() + "@courses.test");
        grantTenantRole(school, owner, "tenant_admin");
        UUID course = insertCourse(school, owner, "Алгебра " + Ids.newId());
        UUID student = createUser(school, "student-" + Ids.newId() + "@courses.test");
        enrollment.enrol(new EnrolCommand(school, course, owner, CourseRole.TEACHER, EnrolCommand.METHOD_MANUAL, owner));
        enrollment.enrol(new EnrolCommand(school, course, student, CourseRole.STUDENT, EnrolCommand.METHOD_MANUAL, owner));

        UUID otherSchool = createTenant("other-courses-school");
        UUID otherOwner = createUser(otherSchool, "other-" + Ids.newId() + "@courses.test");
        UUID otherCourse = insertCourse(otherSchool, otherOwner, "Физика " + Ids.newId());

        mvc.perform(get("/api/v1/platform/courses").param("tenantId", school.toString())
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].id").value(course.toString()))
            .andExpect(jsonPath("$.items[0].tenantId").value(school.toString()))
            .andExpect(jsonPath("$.items[0].author.id").value(owner.toString()))
            .andExpect(jsonPath("$.items[0].studentsCount").value(1))
            .andExpect(jsonPath("$.items[0].staffCount").value(1));

        mvc.perform(get("/api/v1/platform/courses").param("q", "Физика")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[?(@.id == '%s')].tenantId", otherCourse.toString())
                    .value(otherSchool.toString()))
            .andExpect(jsonPath("$.items[?(@.id == '%s')]", course.toString()).isEmpty());

        mvc.perform(get("/api/v1/platform/courses").header(HttpHeaders.AUTHORIZATION, bearer(school, owner)))
            .andExpect(status().isForbidden());
    }

    private UUID insertCourse(UUID tenantId, UUID createdBy, String title) {
        UUID id = Ids.newId();
        jdbc.sql("""
                INSERT INTO courses (id, tenant_id, title, slug, created_by, created_at, updated_at)
                VALUES (:id, :tenantId, :title, :slug, :createdBy, now(), now())
                """)
            .param("id", id).param("tenantId", tenantId).param("title", title).param("slug", "c-" + id)
            .param("createdBy", createdBy).update();
        return id;
    }
}
