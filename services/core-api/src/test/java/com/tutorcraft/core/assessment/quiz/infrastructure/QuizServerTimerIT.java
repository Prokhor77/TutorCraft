package com.tutorcraft.core.assessment.quiz.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.tutorcraft.core.assessment.quiz.QuizIntegrationSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * AC-4 (FR-QUIZ-03, NFR-SEC-08): серверный таймер. Ответы после time_due (+ допуск) отклоняются, просроченную попытку
 * завершает фоновая задача, сохранённые ответы оцениваются; ключи не попадают ни в один ответ API до завершения.
 */
class QuizServerTimerIT extends QuizIntegrationSupport {

    private static final int TIME_LIMIT_SEC = 1200;
    private static final String SECRET_KEY = "Photosynthesis-KEY";

    @Autowired
    private QuizJobs jobs;

    private UUID student;
    private UUID itemId;

    @BeforeEach
    void setUp() throws Exception {
        setUpTenant("quiz-timer");
        student = createUser(tenant, unique("student"));
        itemId = publishedQuiz(Map.of("timeLimitSec", TIME_LIMIT_SEC, "review", reviewImmediately()), List.of(student));
        UUID courseId = courseOf(itemId);
        UUID choice = createQuestion(courseId, question("single_choice", "Capital of France", Map.of("options",
                List.of(option("a", "Paris", true), option("b", "Lyon", false)), "shuffle", false)));
        UUID text = createQuestion(courseId, question("short_answer", "Process in leaves", Map.of("answers",
                List.of(Map.of("pattern", SECRET_KEY, "scorePercent", 100)), "caseSensitive", false)));
        putSlots(itemId, List.of(choice, text));
    }

    @Test
    void answersAfterTimeDueAreRejectedAndServerFinishesTheAttempt() throws Exception {
        String started = send(post(API + "/items/" + itemId + "/attempts"), null, student, 200);
        assertNoKeys(started);
        String attemptId = JsonPath.read(started, "$.id");
        Instant startedAt = Instant.parse(JsonPath.read(started, "$.startedAt"));
        assertThat(Instant.parse((String) JsonPath.read(started, "$.timeDue"))).isEqualTo(startedAt.plusSeconds(TIME_LIMIT_SEC));
        assertThat((String) JsonPath.read(started, "$.serverNow")).isNotBlank();

        send(put(API + "/attempts/" + attemptId + "/answers/1"), Map.of("response", Map.of("optionId", "a")), student, 200);
        assertNoKeys(send(get(API + "/attempts/" + attemptId), null, student, 200));
        perform(get(API + "/attempts/" + attemptId + "/result"), student)
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("quiz.attempt_in_progress"));

        moveDeadlineToThePast(UUID.fromString(attemptId));
        perform(put(API + "/attempts/" + attemptId + "/answers/2").contentType(MediaType.APPLICATION_JSON)
                .content("{\"response\":{\"text\":\"" + SECRET_KEY + "\"}}"), student)
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("quiz.time_expired"));

        jobs.finishExpiredAttempts();
        String result = send(get(API + "/attempts/" + attemptId + "/result"), null, student, 200);
        assertThat((String) JsonPath.read(result, "$.state")).isEqualTo("finished");
        assertThat(((Number) JsonPath.read(result, "$.questions[0].score")).doubleValue()).isEqualTo(1.0);
        assertThat(((Number) JsonPath.read(result, "$.questions[1].score")).doubleValue()).isZero();
        assertThat((Object) JsonPath.read(result, "$.questions[1].response")).isNull();
        assertThat((String) JsonPath.read(result, "$.questions[1].correctResponse.text")).isEqualTo(SECRET_KEY);
    }

    @Test
    void attemptIsInvisibleToOtherStudentsAndOtherTenants() throws Exception {
        String attemptId = JsonPath.read(send(post(API + "/items/" + itemId + "/attempts"), null, student, 200), "$.id");
        UUID classmate = createUser(tenant, unique("classmate"));
        perform(get(API + "/attempts/" + attemptId), classmate).andExpect(status().isForbidden());
        perform(put(API + "/attempts/" + attemptId + "/answers/1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"response\":{\"optionId\":\"a\"}}"), classmate)
            .andExpect(status().isNotFound());
        UUID otherTenant = createTenant("quiz-other");
        UUID stranger = createUser(otherTenant, unique("stranger"));
        perform(get(API + "/attempts/" + attemptId), otherTenant, stranger).andExpect(status().isNotFound());
        perform(post(API + "/items/" + itemId + "/attempts"), otherTenant, stranger).andExpect(status().isNotFound());
    }

    @Test
    void startingTwiceContinuesTheSameAttempt() throws Exception {
        String first = JsonPath.read(send(post(API + "/items/" + itemId + "/attempts"), null, student, 200), "$.id");
        String second = JsonPath.read(send(post(API + "/items/" + itemId + "/attempts"), null, student, 200), "$.id");
        assertThat(second).isEqualTo(first);
    }

    private void moveDeadlineToThePast(UUID attemptId) {
        Instant now = Instant.now();
        jdbc.sql("UPDATE quiz_attempts SET started_at = :startedAt, time_due = :timeDue WHERE id = :id")
            .param("startedAt", java.sql.Timestamp.from(now.minus(Duration.ofMinutes(21))))
            .param("timeDue", java.sql.Timestamp.from(now.minus(Duration.ofMinutes(1))))
            .param("id", attemptId)
            .update();
    }

    private static void assertNoKeys(String body) {
        assertThat(body).doesNotContain("\"correct\"", SECRET_KEY, "scorePercent", "\"feedback\"", "correctResponse");
    }
}
