package com.tutorcraft.core.assessment.quiz.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.tutorcraft.core.assessment.quiz.application.AttemptRepository.ItemAttemptState;
import org.junit.jupiter.api.Test;

/** Статус теста в оглавлении и «Моих задачах». */
class QuizItemStatusProviderTest {

    @Test
    void statusFollowsAttempts() {
        assertThat(QuizItemStatusProvider.status(null)).isEqualTo("not_started");
        assertThat(QuizItemStatusProvider.status(new ItemAttemptState(true, 1, false))).isEqualTo("in_progress");
        assertThat(QuizItemStatusProvider.status(new ItemAttemptState(false, 0, false))).isEqualTo("not_started");
        assertThat(QuizItemStatusProvider.status(new ItemAttemptState(false, 2, true))).isEqualTo("submitted");
        assertThat(QuizItemStatusProvider.status(new ItemAttemptState(false, 1, false))).isEqualTo("graded");
    }
}
