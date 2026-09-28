package com.tutorcraft.core.assessment.assignment.web;

import com.tutorcraft.core.assessment.assignment.application.ExtensionService;
import com.tutorcraft.core.assessment.assignment.application.ExtensionService.ExtensionCommand;
import com.tutorcraft.core.assessment.assignment.application.ExtensionService.ExtensionView;
import com.tutorcraft.core.assessment.assignment.application.GradingService;
import com.tutorcraft.core.assessment.assignment.application.GradingService.GradeCommand;
import com.tutorcraft.core.assessment.assignment.application.SubmissionReviewService;
import com.tutorcraft.core.assessment.assignment.application.SubmissionView;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.api.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Проверка заданий (контракт §8, FR-ASSIGN-03/05/06/07). */
@RestController
@RequestMapping("/api/v1")
class SubmissionReviewController {

    private static final int MAX_FEEDBACK_FILES = 20;

    private final SubmissionReviewService review;
    private final GradingService grading;
    private final ExtensionService extensions;

    SubmissionReviewController(SubmissionReviewService review, GradingService grading, ExtensionService extensions) {
        this.review = review;
        this.grading = grading;
        this.extensions = extensions;
    }

    @GetMapping("/items/{itemId}/submissions")
    PageResponse<SubmissionView.Summary> list(@PathVariable UUID itemId, @RequestParam(required = false) String status,
                                              @RequestParam(required = false) UUID groupId,
                                              @RequestParam(required = false) String cursor,
                                              @RequestParam(required = false) Integer limit) {
        return review.list(itemId, status, groupId, PageQuery.of(cursor, limit));
    }

    @GetMapping("/submissions/{submissionId}")
    SubmissionView get(@PathVariable UUID submissionId) {
        return review.get(submissionId);
    }

    @PostMapping("/submissions/{submissionId}/grade")
    SubmissionView grade(@PathVariable UUID submissionId, @Valid @RequestBody GradeRequest request) {
        return grading.grade(submissionId, new GradeCommand(request.score(), request.feedback(), request.feedbackFileIds(),
                Boolean.TRUE.equals(request.returnForRevision())));
    }

    @PostMapping("/items/{itemId}/grades/publish")
    PublishResult publish(@PathVariable UUID itemId) {
        return new PublishResult(grading.publishAll(itemId));
    }

    @PostMapping("/items/{itemId}/extensions")
    ExtensionView grantExtension(@PathVariable UUID itemId, @Valid @RequestBody ExtensionRequest request) {
        return extensions.grant(itemId, new ExtensionCommand(request.userId(), request.groupId(), request.dueAt(),
                request.closeAt()));
    }

    @GetMapping("/items/{itemId}/extensions")
    List<ExtensionView> listExtensions(@PathVariable UUID itemId) {
        return extensions.list(itemId);
    }

    @DeleteMapping("/extensions/{extensionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeExtension(@PathVariable UUID extensionId) {
        extensions.revoke(extensionId);
    }

    record GradeRequest(@DecimalMin("0") BigDecimal score, Map<String, Object> feedback,
                        @Size(max = MAX_FEEDBACK_FILES) List<@NotNull UUID> feedbackFileIds, Boolean returnForRevision) {
    }

    record ExtensionRequest(UUID userId, UUID groupId, @NotNull Instant dueAt, Instant closeAt) {
    }

    record PublishResult(int published) {
    }
}
