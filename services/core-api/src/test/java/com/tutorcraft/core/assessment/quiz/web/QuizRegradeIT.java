package com.tutorcraft.core.assessment.quiz.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.jayway.jsonpath.JsonPath;
import com.tutorcraft.core.assessment.quiz.QuizIntegrationSupport;
import com.tutorcraft.core.gradebook.GradebookApi;
import com.tutorcraft.core.gradebook.GradebookApi.GradeView;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * AC-5 (DATA-02, FR-QBANK-05, FR-QUIZ-07): правка вопроса создаёт новую версию и не меняет завершённые попытки;
 * «Переоценить» переводит попытки на новую версию, пересчитывает журнал (история изменений и уведомления о
 * пересчитанной опубликованной оценке — в gradebook).
 */
class QuizRegradeIT extends QuizIntegrationSupport {

    private static final int LEARNERS = 3;
    private static final BigDecimal MAX_SCORE = BigDecimal.TEN;

    @Autowired
    private GradebookApi gradebook;

    @Test
    void fixingTheKeyChangesNothingUntilRegrade() throws Exception {
        setUpTenant("quiz-regrade");
        List<UUID> students = new ArrayList<>();
        for (int i = 0; i < LEARNERS; i++) {
            students.add(createUser(tenant, unique("student")));
        }
        UUID itemId = publishedQuiz(Map.of("maxScore", MAX_SCORE.intValue(), "review", reviewImmediately()), students);
        UUID courseId = courseOf(itemId);
        Map<String, Object> wrongKey = question("single_choice", "2 + 2", Map.of("options",
                List.of(option("a", "4", false), option("b", "5", true)), "shuffle", false));
        UUID questionId = createQuestion(courseId, wrongKey);
        putSlots(itemId, List.of(questionId));
        List<String> attempts = new ArrayList<>();
        for (UUID student : students) {
            attempts.add(answerAndFinish(itemId, student, "a"));
        }
        assertThat(score(attempts.get(0), students.get(0))).isZero();

        Map<String, Object> fixedKey = question("single_choice", "2 + 2", Map.of("options",
                List.of(option("a", "4", true), option("b", "5", false)), "shuffle", false));
        String updated = send(put(API + "/questions/" + questionId), fixedKey, teacher, 200);
        assertThat((Integer) JsonPath.read(updated, "$.version")).isEqualTo(2);
        assertThat(score(attempts.get(0), students.get(0))).isZero();
        assertThat(gradebook.grade(tenant, itemId, students.get(0))).map(GradeView::score)
                .hasValueSatisfying(value -> assertThat(value).isEqualByComparingTo("0"));

        String regraded = send(post(API + "/items/" + itemId + "/regrade"), null, teacher, 200);
        assertThat((Integer) JsonPath.read(regraded, "$.regraded")).isEqualTo(LEARNERS);
        for (int i = 0; i < LEARNERS; i++) {
            assertThat(score(attempts.get(i), students.get(i))).isEqualTo(MAX_SCORE.doubleValue());
            assertThat(gradebook.grade(tenant, itemId, students.get(i))).map(GradeView::score)
                    .hasValueSatisfying(value -> assertThat(value).isEqualByComparingTo(MAX_SCORE));
        }
        String report = send(get(API + "/items/" + itemId + "/attempts"), null, teacher, 200);
        assertThat((List<?>) JsonPath.read(report, "$.items")).hasSize(LEARNERS);
    }

    @Test
    void studentCannotRegradeOrReadTheKey() throws Exception {
        setUpTenant("quiz-regrade-deny");
        UUID student = createUser(tenant, unique("student"));
        UUID itemId = publishedQuiz(Map.of(), List.of(student));
        UUID questionId = createQuestion(courseOf(itemId), question("true_false", "Sky is blue", Map.of("correct", true)));
        send(post(API + "/items/" + itemId + "/regrade"), null, student, 403);
        send(get(API + "/questions/" + questionId), null, student, 403);
        send(get(API + "/items/" + itemId + "/attempts"), null, student, 403);
    }

    private String answerAndFinish(UUID itemId, UUID student, String optionId) throws Exception {
        String attemptId = JsonPath.read(send(post(API + "/items/" + itemId + "/attempts"), null, student, 200), "$.id");
        send(put(API + "/attempts/" + attemptId + "/answers/1"), Map.of("response", Map.of("optionId", optionId)), student, 200);
        String key = UUID.randomUUID().toString();
        String first = perform(post(API + "/attempts/" + attemptId + "/finish").header("Idempotency-Key", key), student)
                .andReturn().getResponse().getContentAsString();
        String repeated = perform(post(API + "/attempts/" + attemptId + "/finish").header("Idempotency-Key", key), student)
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(repeated, "$.id")).isEqualTo(attemptId);
        assertThat((String) JsonPath.read(first, "$.state")).isEqualTo("finished");
        assertThat(((Number) JsonPath.read(repeated, "$.score")).doubleValue())
                .isEqualTo(((Number) JsonPath.read(first, "$.score")).doubleValue());
        return attemptId;
    }

    private double score(String attemptId, UUID student) throws Exception {
        String result = send(get(API + "/attempts/" + attemptId + "/result"), null, student, 200);
        return ((Number) JsonPath.read(result, "$.score")).doubleValue();
    }
}
