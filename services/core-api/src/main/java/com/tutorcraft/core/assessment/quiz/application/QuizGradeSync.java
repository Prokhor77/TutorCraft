package com.tutorcraft.core.assessment.quiz.application;

import com.tutorcraft.core.assessment.quiz.domain.GradingMethod.ScoredAttempt;
import com.tutorcraft.core.assessment.quiz.domain.QuizSettings;
import com.tutorcraft.core.assessment.quiz.domain.ReviewTiming;
import com.tutorcraft.core.courses.CoursesApi;
import com.tutorcraft.core.courses.ItemRef;
import com.tutorcraft.core.gradebook.GradebookApi;
import com.tutorcraft.core.gradebook.GradebookApi.GradeUpdate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Итоговая оценка за тест в журнал (FR-QUIZ-01, FR-GRADE): агрегирует завершённые попытки методом теста.
 * Публикация: сразу — если {@code review.whenScore = immediately} и нет непроверенных эссе;
 * {@code after_close} — отложенная публикация в момент закрытия; {@code never} — вручную преподавателем.
 */
@Component
public class QuizGradeSync {

    private final AttemptRepository attempts;
    private final GradeReleaseRepository releases;
    private final GradebookApi gradebook;
    private final CoursesApi courses;

    public QuizGradeSync(AttemptRepository attempts, GradeReleaseRepository releases, GradebookApi gradebook,
                         CoursesApi courses) {
        this.attempts = attempts;
        this.releases = releases;
        this.gradebook = gradebook;
        this.courses = courses;
    }

    /** @return записанная итоговая оценка; пусто — нет завершённых попыток или тест удалён */
    public Optional<BigDecimal> sync(UUID tenantId, UUID itemId, UUID userId, UUID graderId) {
        ItemRef item = courses.findItems(tenantId, List.of(itemId)).get(itemId);
        if (item == null) {
            return Optional.empty();
        }
        QuizSettings settings = QuizSettings.parse(item.settings());
        List<ScoredAttempt> scores = attempts.finishedScores(tenantId, itemId, userId);
        Optional<BigDecimal> aggregate = settings.gradingMethod().aggregate(scores);
        aggregate.ifPresent(score -> record(item, settings, userId, score, graderId));
        return aggregate;
    }

    private void record(ItemRef item, QuizSettings settings, UUID userId, BigDecimal score, UUID graderId) {
        ReviewTiming whenScore = settings.review().whenScore();
        boolean pending = attempts.hasPendingManual(item.tenantId(), item.id(), userId);
        boolean publish = whenScore == ReviewTiming.IMMEDIATELY && !pending;
        gradebook.recordGrade(new GradeUpdate(item.tenantId(), item.courseId(), item.id(), userId, score, graderId, publish));
        if (whenScore == ReviewTiming.AFTER_CLOSE && settings.closeAt() != null) {
            releases.schedule(item.tenantId(), item.courseId(), item.id(), settings.closeAt());
        }
    }
}
