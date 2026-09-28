package com.tutorcraft.core.gradebook.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.CourseRole;
import com.tutorcraft.core.courses.CourseRef;
import com.tutorcraft.core.courses.CoursesApi;
import com.tutorcraft.core.courses.ItemRef;
import com.tutorcraft.core.courses.ItemType;
import com.tutorcraft.core.courses.Visibility;
import com.tutorcraft.core.enrollment.EnrollmentApi;
import com.tutorcraft.core.gradebook.spi.GradingQueueSource;
import com.tutorcraft.core.gradebook.spi.GradingQueueSource.QueueEntry;
import com.tutorcraft.core.shared.domain.Ids;
import com.tutorcraft.core.support.IntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;

/**
 * AC-8 (FR-GRADE-06): преподаватель трёх курсов с 12 непроверенными работами и 4 эссе видит 16 элементов,
 * отсортированных по сроку; пагинация курсором не теряет и не дублирует записи; чужой tenant ничего не видит.
 */
class GradingQueueIT extends IntegrationTest {

    private static final int COURSES = 3;
    private static final int STUDENTS = 4;
    private static final List<QueueEntry> ESSAYS = new CopyOnWriteArrayList<>();

    @MockBean
    private CoursesApi courses;
    @MockBean
    private EnrollmentApi enrollment;
    @MockBean
    private AccessService access;

    private UUID tenant;
    private UUID teacher;
    private final List<UUID> courseIds = new ArrayList<>();

    @TestConfiguration
    static class EssaySourceConfig {

        /** Эссе из тестов (модуль quiz) — источник-заглушка рядом с реальными. */
        @Bean
        GradingQueueSource testEssaySource() {
            return (tenantId, courseIdsFilter) -> ESSAYS.stream()
                    .filter(entry -> courseIdsFilter.contains(entry.courseId()))
                    .toList();
        }
    }

    @BeforeEach
    void setUp() {
        tenant = createTenant("queue");
        teacher = createUser(tenant, unique("teacher"));
        courseIds.clear();
        ESSAYS.clear();
        for (int course = 0; course < COURSES; course++) {
            courseIds.add(Ids.newId());
        }
        List<UUID> students = new ArrayList<>();
        for (int index = 0; index < STUDENTS + 2; index++) {
            students.add(createUser(tenant, unique("student")));
        }
        Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        seedSubmissions(students, base);
        seedEssays(students, base);
        stubCollaborators();
    }

    @Test
    void teacherSeesAllPendingWorkSortedByDueDate() throws Exception {
        String body = mvc.perform(get("/api/v1/grading-queue?limit=100").header(HttpHeaders.AUTHORIZATION, bearer(tenant, teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(16))
                .andExpect(jsonPath("$.nextCursor").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        List<String> kinds = JsonPath.read(body, "$.items[*].kind");
        assertThat(kinds.stream().filter("essay"::equals).count()).isEqualTo(4);
        List<String> dues = JsonPath.read(body, "$.items[*].dueAt");
        List<Instant> nonNull = dues.stream().filter(value -> value != null).map(Instant::parse).toList();
        assertThat(nonNull).isSorted();
        assertThat(dues.subList(nonNull.size(), dues.size())).allMatch(value -> value == null);
        List<String> titles = JsonPath.read(body, "$.items[*].courseTitle");
        assertThat(titles).doesNotContainNull();
    }

    @Test
    void cursorPaginationCoversQueueWithoutDuplicates() throws Exception {
        String auth = bearer(tenant, teacher);
        String first = mvc.perform(get("/api/v1/grading-queue?limit=10").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(10))
                .andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(first, "$.nextCursor");

        String second = mvc.perform(get("/api/v1/grading-queue?limit=10&cursor=" + cursor).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(6))
                .andExpect(jsonPath("$.nextCursor").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        Set<String> ids = new HashSet<>(JsonPath.<List<String>>read(first, "$.items[*].id"));
        ids.addAll(JsonPath.<List<String>>read(second, "$.items[*].id"));
        assertThat(ids).hasSize(16);
    }

    @Test
    void courseFilterAndTypeFilterNarrowTheQueue() throws Exception {
        mvc.perform(get("/api/v1/grading-queue?limit=100&type=essay").header(HttpHeaders.AUTHORIZATION, bearer(tenant, teacher)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(4));
        mvc.perform(get("/api/v1/grading-queue?limit=100&courseId=" + courseIds.get(0))
                .header(HttpHeaders.AUTHORIZATION, bearer(tenant, teacher)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].courseId").value(everyItem(equalTo(courseIds.get(0).toString()))));
    }

    @Test
    void teacherOfAnotherTenantSeesNothing() throws Exception {
        UUID otherTenant = createTenant("queue-other");
        UUID otherTeacher = createUser(otherTenant, unique("teacher"));

        mvc.perform(get("/api/v1/grading-queue").header(HttpHeaders.AUTHORIZATION, bearer(otherTenant, otherTeacher)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(0));
    }

    /** 12 отправленных работ (3 курса × 4 студента, у одного курса нет срока) + черновик и проверенная — не в очереди. */
    private void seedSubmissions(List<UUID> students, Instant base) {
        for (int course = 0; course < COURSES; course++) {
            UUID itemId = itemIdFor(course);
            Instant due = course == COURSES - 1 ? null : base.plus(course + 1L, ChronoUnit.DAYS);
            for (int student = 0; student < STUDENTS; student++) {
                insertSubmission(courseIds.get(course), itemId, students.get(student), "submitted",
                        base.minus(student + 1L, ChronoUnit.HOURS), due);
            }
        }
        insertSubmission(courseIds.get(0), itemIdFor(0), students.get(STUDENTS), "draft", null, null);
        insertSubmission(courseIds.get(0), itemIdFor(0), students.get(STUDENTS + 1), "graded", base, null);
    }

    private void seedEssays(List<UUID> students, Instant base) {
        for (int essay = 0; essay < 4; essay++) {
            UUID courseId = courseIds.get(essay % COURSES);
            ESSAYS.add(new QueueEntry(QueueEntry.ESSAY, Ids.newId() + ":" + (essay + 1), courseId,
                    UUID.nameUUIDFromBytes(("quiz-" + courseId).getBytes()), null, students.get(essay),
                    base.minus(essay, ChronoUnit.MINUTES), base.plus(essay, ChronoUnit.HOURS), false));
        }
    }

    private void stubCollaborators() {
        when(enrollment.activeCourseIds(eq(tenant), eq(teacher), any())).thenAnswer(invocation -> {
            Set<CourseRole> roles = invocation.getArgument(2);
            return roles.contains(CourseRole.TEACHER) ? List.copyOf(courseIds) : List.of();
        });
        when(access.can(any(), any())).thenReturn(true);
        when(courses.findItems(eq(tenant), any())).thenAnswer(invocation -> {
            Collection<UUID> ids = invocation.getArgument(1);
            return ids.stream().collect(Collectors.toMap(Function.identity(), this::item));
        });
        when(courses.findCourses(eq(tenant), any())).thenAnswer(invocation -> {
            Collection<UUID> ids = invocation.getArgument(1);
            return ids.stream().collect(Collectors.toMap(Function.identity(), this::course));
        });
    }

    private ItemRef item(UUID itemId) {
        return new ItemRef(itemId, tenant, courseIds.get(0), Ids.newId(), ItemType.ASSIGNMENT, "Задание " + itemId, 0,
                Visibility.PUBLISHED, null, null, null, null, Map.of(), Map.of(), null, 1);
    }

    private CourseRef course(UUID courseId) {
        return new CourseRef(courseId, tenant, "Курс " + courseId, null, "c-" + courseId, null, Visibility.PUBLISHED, null, null,
                null, null, List.of(), null, "none", teacher);
    }

    private UUID itemIdFor(int course) {
        return UUID.nameUUIDFromBytes(("assignment-" + courseIds.get(course)).getBytes());
    }

    private void insertSubmission(UUID courseId, UUID itemId, UUID studentId, String status, Instant submittedAt, Instant due) {
        UUID id = Ids.newId();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO submissions (id, tenant_id, course_id, item_id, user_id, owner_key, attempt_no, is_latest, status,
                                         submitted_at, due_at, late, created_at, updated_at)
                VALUES (:id, :tenantId, :courseId, :itemId, :userId, :ownerKey, 1, TRUE, :status, :submittedAt, :dueAt, FALSE,
                        :now, :now)
                """)
            .param("id", id).param("tenantId", tenant).param("courseId", courseId).param("itemId", itemId)
            .param("userId", studentId).param("ownerKey", "u:" + studentId).param("status", status)
            .param("submittedAt", submittedAt == null ? null : Timestamp.from(submittedAt))
            .param("dueAt", due == null ? null : Timestamp.from(due)).param("now", now)
            .update();
        jdbc.sql("INSERT INTO submission_members (tenant_id, submission_id, user_id) VALUES (:tenantId, :id, :userId)")
            .param("tenantId", tenant).param("id", id).param("userId", studentId)
            .update();
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.test";
    }
}
