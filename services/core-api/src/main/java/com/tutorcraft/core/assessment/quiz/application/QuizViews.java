package com.tutorcraft.core.assessment.quiz.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tutorcraft.core.assessment.quiz.domain.PublicQuestionParts.Labeled;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Представления тестов и банка вопросов (контракт §10). */
public final class QuizViews {

    private QuizViews() {
    }

    /** Вопрос с ключами — только для qbank.manage. */
    public record QuestionView(UUID id, int version, UUID versionId, String type, String title, Map<String, Object> body,
                               BigDecimal defaultScore, UUID categoryId, List<String> tags, Map<String, Object> data,
                               Map<String, Object> generalFeedback) {
    }

    public record QuestionSummaryView(UUID id, String type, String title, List<String> tags, int version, Instant updatedAt,
                                      int usedInQuizzes) {
    }

    public record QuestionVersionView(int version, Instant createdAt, UUID id) {
    }

    public record CategoryView(UUID id, UUID parentId, String name, long questionCount) {
    }

    public record PreviewCheckView(BigDecimal score, BigDecimal maxScore, boolean correct) {
    }

    /**
     * Вопрос попытки для студента (NFR-SEC-08): только публичные части, без ключей и отзывов.
     * Поля, неприменимые к типу, не сериализуются.
     */
    public record StudentQuestionView(int slot, int page, BigDecimal points, String type, String title,
                                      Map<String, Object> body,
                                      @JsonInclude(JsonInclude.Include.NON_NULL) List<Labeled> options,
                                      @JsonInclude(JsonInclude.Include.NON_NULL) List<Labeled> prompts,
                                      @JsonInclude(JsonInclude.Include.NON_NULL) List<String> answerChoices,
                                      @JsonInclude(JsonInclude.Include.NON_NULL) List<Labeled> items,
                                      @JsonInclude(JsonInclude.Include.NON_NULL) String responseFormat,
                                      Map<String, Object> response, boolean flagged) {
    }

    public record AttemptView(UUID id, UUID itemId, int number, String state, Instant startedAt, Instant timeDue,
                              Instant serverNow, List<StudentQuestionView> questions, int totalPages) {
    }

    public record AttemptResultView(UUID id, String state, BigDecimal score, BigDecimal maxScore, Double percent,
                                    Boolean passed, boolean needsManualGrading, List<ResultQuestion> questions) {
    }

    public record ResultQuestion(int slot, String title, BigDecimal score, BigDecimal points, Boolean correct,
                                 Map<String, Object> response,
                                 @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> correctResponse,
                                 @JsonInclude(JsonInclude.Include.NON_NULL) String feedback,
                                 @JsonInclude(JsonInclude.Include.NON_NULL) String comment) {
    }

    public record AttemptSummaryView(UUID id, UUID userId, String userName, int number, String state, Instant startedAt,
                                     Instant finishedAt, BigDecimal score, BigDecimal maxScore) {
    }

    public record SavedAnswerView(Instant savedAt) {
    }

    public record RegradeView(int regraded) {
    }

    public record OverrideView(UUID id, UUID userId, UUID groupId, Instant openAt, Instant closeAt, Integer timeLimitSec,
                               Integer maxAttempts) {
    }

    /** Слот состава в ответе GET: либо questionId, либо random. */
    public record SlotView(@JsonInclude(JsonInclude.Include.NON_NULL) UUID questionId,
                           @JsonInclude(JsonInclude.Include.NON_NULL) RandomView random,
                           BigDecimal points, Integer page) {
    }

    public record RandomView(UUID categoryId, String tag, int count) {
    }

    public record LayoutView(List<SlotView> slots, List<QuestionSummaryView> questions) {
    }
}
