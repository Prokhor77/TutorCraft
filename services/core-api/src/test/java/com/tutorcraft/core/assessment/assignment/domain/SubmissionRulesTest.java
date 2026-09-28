package com.tutorcraft.core.assessment.assignment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tutorcraft.core.shared.domain.BusinessRuleException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Правила сдачи: окно открытия/закрытия, опоздание, правка, отправка, попытки (FR-ASSIGN-02/04, AC-3). */
class SubmissionRulesTest {

    private static final Instant OPEN = Instant.parse("2026-09-01T08:00:00Z");
    private static final Instant DUE = Instant.parse("2026-09-10T20:59:59Z");
    private static final Instant CLOSE = Instant.parse("2026-09-12T20:59:59Z");
    private static final Map<String, Object> TEXT = Map.of("schemaVersion", 1, "blocks", List.of(Map.of("id", "b1")));

    private final EffectiveDeadlines deadlines = new EffectiveDeadlines(OPEN, DUE, CLOSE);

    @Test
    void submissionBeforeOpenIsRejected() {
        assertThatThrownBy(() -> SubmissionRules.requireWindowOpen(deadlines, OPEN.minusSeconds(1)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).code()).isEqualTo(AssignmentErrors.NOT_OPEN);
    }

    @Test
    void hardCloseBlocksSubmissionAfterCloseAtButNotAtIt() {
        assertThatCode(() -> SubmissionRules.requireWindowOpen(deadlines, CLOSE)).doesNotThrowAnyException();
        assertThatThrownBy(() -> SubmissionRules.requireWindowOpen(deadlines, CLOSE.plusSeconds(1)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).code()).isEqualTo(AssignmentErrors.CLOSED);
    }

    @Test
    void windowWithoutDatesIsAlwaysOpen() {
        assertThatCode(() -> SubmissionRules.requireWindowOpen(new EffectiveDeadlines(null, null, null), OPEN))
                .doesNotThrowAnyException();
    }

    @Test
    void lateOnlyAfterDueAt() {
        assertThat(SubmissionRules.isLate(deadlines, DUE)).isFalse();
        assertThat(SubmissionRules.isLate(deadlines, DUE.plusSeconds(1))).isTrue();
        assertThat(SubmissionRules.isLate(new EffectiveDeadlines(null, null, null), CLOSE)).isFalse();
    }

    @Test
    void draftIsSubmittableWithContent() {
        Submission draft = draft().withContent(TEXT, List.of(), OPEN);
        assertThatCode(() -> SubmissionRules.requireSubmittable(draft, AssignmentSettings.defaults())).doesNotThrowAnyException();
    }

    @Test
    void emptyDraftCannotBeSubmitted() {
        assertThatThrownBy(() -> SubmissionRules.requireSubmittable(draft(), AssignmentSettings.defaults()))
                .extracting(e -> ((BusinessRuleException) e).code()).isEqualTo(AssignmentErrors.EMPTY_SUBMISSION);
    }

    @Test
    void submittedWorkCannotBeSubmittedAgain() {
        Submission submitted = draft().withContent(TEXT, List.of(), OPEN).submitted(OPEN, DUE, false);
        assertThatThrownBy(() -> SubmissionRules.requireSubmittable(submitted, AssignmentSettings.defaults()))
                .extracting(e -> ((BusinessRuleException) e).code()).isEqualTo(AssignmentErrors.ALREADY_SUBMITTED);
    }

    @Test
    void offlineAssignmentRejectsOnlineSubmission() {
        AssignmentSettings offline = settings(SubmissionType.NONE, true, null);
        assertThatThrownBy(() -> SubmissionRules.requireSubmittable(draft(), offline))
                .extracting(e -> ((BusinessRuleException) e).code()).isEqualTo(AssignmentErrors.OFFLINE);
    }

    @Test
    void submittedWorkIsEditableOnlyWithoutSubmitButton() {
        Submission submitted = draft().submitted(OPEN, DUE, false);
        assertThatThrownBy(() -> SubmissionRules.requireEditable(submitted, settings(SubmissionType.FILE, true, null)))
                .extracting(e -> ((BusinessRuleException) e).code()).isEqualTo(AssignmentErrors.NOT_EDITABLE);
        assertThatCode(() -> SubmissionRules.requireEditable(submitted, settings(SubmissionType.FILE, false, null)))
                .doesNotThrowAnyException();
        Submission graded = submitted.withStatus(SubmissionStatus.GRADED, OPEN);
        assertThatThrownBy(() -> SubmissionRules.requireEditable(graded, settings(SubmissionType.FILE, false, null)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatCode(() -> SubmissionRules.requireEditable(draft(), AssignmentSettings.defaults())).doesNotThrowAnyException();
    }

    @Test
    void submissionStatusReflectsLateness() {
        assertThat(draft().submitted(DUE.plusSeconds(5), DUE, true).status()).isEqualTo(SubmissionStatus.SUBMITTED_LATE);
        assertThat(draft().submitted(DUE, DUE, false).status()).isEqualTo(SubmissionStatus.SUBMITTED);
    }

    @Test
    void newAttemptOnlyAfterReturnAndWithinLimit() {
        assertThat(SubmissionRules.needsNewAttempt(draft().withStatus(SubmissionStatus.RETURNED, OPEN))).isTrue();
        assertThat(SubmissionRules.needsNewAttempt(draft().withStatus(SubmissionStatus.GRADED, OPEN))).isFalse();
        assertThatCode(() -> SubmissionRules.requireAttemptAvailable(settings(SubmissionType.FILE, true, null), 50))
                .doesNotThrowAnyException();
        assertThatCode(() -> SubmissionRules.requireAttemptAvailable(settings(SubmissionType.FILE, true, 2), 1))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> SubmissionRules.requireAttemptAvailable(settings(SubmissionType.FILE, true, 2), 2))
                .extracting(e -> ((BusinessRuleException) e).code()).isEqualTo(AssignmentErrors.ATTEMPTS_EXHAUSTED);
    }

    private static Submission draft() {
        return Submission.newDraft(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), null, 1, OPEN);
    }

    private static AssignmentSettings settings(SubmissionType type, boolean requireSubmit, Integer maxAttempts) {
        return new AssignmentSettings(type, BigDecimal.TEN, DUE, OPEN, CLOSE, List.of(), 5, 50, maxAttempts, false,
                requireSubmit, null, true);
    }
}
