package com.tutorcraft.core.assessment.quiz.infrastructure;

import com.tutorcraft.core.assessment.quiz.application.AttemptFinisher;
import com.tutorcraft.core.assessment.quiz.application.AttemptRepository;
import com.tutorcraft.core.assessment.quiz.application.AttemptRepository.AttemptKey;
import com.tutorcraft.core.assessment.quiz.application.GradeReleaseRepository;
import com.tutorcraft.core.assessment.quiz.application.GradeReleaseRepository.Release;
import com.tutorcraft.core.gradebook.GradebookApi;
import com.tutorcraft.core.shared.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Фоновые задачи тестов:
 * <ul>
 *   <li>завершение просроченных попыток сервером (AC-4): сохранённые ответы оцениваются, публикуется AttemptFinished;</li>
 *   <li>публикация оценок в момент закрытия теста (review.whenScore = after_close).</li>
 * </ul>
 * Каждая попытка/публикация — в своей транзакции; сбой одной не останавливает остальные.
 */
@Component
class QuizJobs {

    private static final Logger log = LoggerFactory.getLogger(QuizJobs.class);
    private static final int BATCH_SIZE = 100;

    private final AttemptRepository attempts;
    private final AttemptFinisher finisher;
    private final GradeReleaseRepository releases;
    private final GradebookApi gradebook;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final Duration grace;

    QuizJobs(AttemptRepository attempts, AttemptFinisher finisher, GradeReleaseRepository releases, GradebookApi gradebook,
             PlatformTransactionManager transactionManager, Clock clock, AppProperties properties) {
        this.attempts = attempts;
        this.finisher = finisher;
        this.releases = releases;
        this.gradebook = gradebook;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.grace = properties.quiz().timeGrace();
    }

    @Scheduled(fixedDelayString = "${tutorcraft.quiz.expiry-poll-interval:PT15S}")
    public void finishExpiredAttempts() {
        Instant now = clock.instant();
        for (AttemptKey key : attempts.findExpired(now.minus(grace), BATCH_SIZE)) {
            try {
                finisher.finish(key.tenantId(), key.attemptId(), now);
            } catch (RuntimeException e) {
                log.error("Auto-finish of attempt {} failed", key.attemptId(), e);
            }
        }
    }

    @Scheduled(fixedDelayString = "${tutorcraft.quiz.release-poll-interval:PT1M}")
    public void releaseGradesOfClosedQuizzes() {
        Instant now = clock.instant();
        for (Release release : releases.findDue(now, BATCH_SIZE)) {
            try {
                transactions.executeWithoutResult(status -> release(release, now));
            } catch (RuntimeException e) {
                log.error("Grade release of quiz {} failed", release.itemId(), e);
            }
        }
    }

    private void release(Release release, Instant now) {
        if (!releases.markReleased(release.tenantId(), release.itemId(), now)) {
            return;
        }
        int published = gradebook.publishAll(release.tenantId(), release.itemId(), null);
        log.info("Quiz {} closed: {} grades published", release.itemId(), published);
    }
}
